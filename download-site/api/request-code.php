<?php
declare(strict_types=1);
require_once __DIR__ . '/bootstrap.php';
require_once __DIR__ . '/smtp.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') rr_json(['ok' => false], 405);
$input = rr_input();
$email = rr_email($input['email'] ?? '');
$purpose = ($input['purpose'] ?? 'app') === 'admin' ? 'admin' : 'app';
if ($purpose === 'admin') {
    $isAdmin=rr_transaction(function(array &$db)use($email):bool{if($email===strtolower(RR_ADMIN_EMAIL))return true;foreach($db['users'] as $user)if($user['email']===$email)return($user['role']??'user')==='admin'&&rr_user_status($user)!=='BLOCKED';return false;});
    if(!$isAdmin)rr_json(['ok'=>false,'error'=>'Nincs adminisztrátori jogosultság.'],403);
}
$now = time();
$code = (string)random_int(100000, 999999);

rr_transaction(function (array &$db) use ($email, $purpose, $now, $code): void {
    foreach ($db['otps'] as $otp) {
        if ($otp['email'] === $email && $otp['createdAt'] > $now - 60) rr_json(['ok' => false, 'error' => 'Új kód egy perc múlva kérhető.'], 429);
    }
    $db['otps'] = array_values(array_filter($db['otps'], fn(array $otp): bool => $otp['expiresAt'] > $now && $otp['email'] !== $email));
    $db['otps'][] = ['email' => $email, 'purpose' => $purpose, 'hash' => hash_hmac('sha256', $code, RR_DATA_KEY), 'expiresAt' => $now + 600, 'createdAt' => $now, 'attempts' => 0];
});

try {
    rr_send_code($email, $code);
    rr_json(['ok' => true, 'message' => 'Az ellenőrzőkódot elküldtük.']);
} catch (Throwable $error) {
    rr_json(['ok' => false, 'error' => 'Az e-mail küldése nem sikerült.'], 503);
}
