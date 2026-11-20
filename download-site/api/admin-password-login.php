<?php
declare(strict_types=1);
require_once __DIR__ . '/bootstrap.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') rr_json(['ok' => false], 405);
$input = rr_input();
$email = rr_email($input['email'] ?? '');
$password = (string)($input['password'] ?? '');
if ($password === '') rr_json(['ok' => false, 'error' => 'Add meg a jelszót.'], 422);
$now = time();
$token = rtrim(strtr(base64_encode(random_bytes(36)), '+/', '-_'), '=');
$result = rr_transaction(function (array &$db) use ($email, $password, $now, $token): array {
    $accountIndex = null;
    foreach (($db['adminAccounts'] ?? []) as $index => $account) if (($account['email'] ?? '') === $email) { $accountIndex = $index; break; }
    if ($accountIndex === null || !password_verify($password, (string)($db['adminAccounts'][$accountIndex]['passwordHash'] ?? ''))) {
        rr_json(['ok' => false, 'error' => 'Hibás e-mail-cím vagy jelszó.'], 401);
    }
    $account = &$db['adminAccounts'][$accountIndex];
    if (!($account['enabled'] ?? true)) rr_json(['ok' => false, 'error' => 'Ez a hozzáférés le van tiltva.'], 403);
    $role = in_array(($account['role'] ?? ''), ['admin','guest'], true) ? $account['role'] : 'guest';
    $account['lastSeenAt'] = $now;
    $db['sessions'][] = ['tokenHash'=>hash('sha256',$token),'userId'=>(string)$account['id'],'deviceId'=>null,'role'=>$role,'adminRole'=>$role,'adminAccountId'=>(string)$account['id'],'createdAt'=>$now,'lastSeenAt'=>$now,'expiresAt'=>$now+300];
    $db['adminLoginLog'][]=['id'=>rr_id('login'),'at'=>$now,'accountId'=>(string)$account['id'],'name'=>(string)($account['name']??''),'email'=>(string)$account['email'],'role'=>$role,'method'=>'PASSWORD','ip'=>(string)($_SERVER['REMOTE_ADDR']??'')];
    $db['adminLoginLog']=array_slice($db['adminLoginLog'],-1000);
    return ['role'=>$role,'displayName'=>(string)($account['name']??''),'email'=>(string)$account['email']];
});
rr_json(['ok'=>true,'token'=>$token]+$result);
