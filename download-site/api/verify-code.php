<?php
declare(strict_types=1);
require_once __DIR__ . '/bootstrap.php';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') rr_json(['ok' => false], 405);
$input = rr_input();
$email = rr_email($input['email'] ?? '');
$code = preg_replace('/\D/', '', (string)($input['code'] ?? ''));
$purpose = ($input['purpose'] ?? 'app') === 'admin' ? 'admin' : 'app';
if (strlen($code) !== 6) rr_json(['ok' => false, 'error' => 'Hat számjegyű kód szükséges.'], 422);
$now = time();
$token = rtrim(strtr(base64_encode(random_bytes(36)), '+/', '-_'), '=');
$result = rr_transaction(function (array &$db) use ($email, $code, $purpose, $input, $now, $token): array {
    $otpIndex = null;
    foreach ($db['otps'] as $index => $otp) {
        if ($otp['email'] === $email && $otp['purpose'] === $purpose && $otp['expiresAt'] > $now) { $otpIndex = $index; break; }
    }
    if ($otpIndex === null || !hash_equals($db['otps'][$otpIndex]['hash'], hash_hmac('sha256', $code, RR_DATA_KEY))) {
        if ($otpIndex !== null) $db['otps'][$otpIndex]['attempts']++;
        rr_json(['ok' => false, 'error' => 'Hibás vagy lejárt ellenőrzőkód.'], 401);
    }
    array_splice($db['otps'], $otpIndex, 1);
    $userId = null;
    foreach ($db['users'] as &$user) if ($user['email'] === $email) { $userId = $user['id']; $user['lastSeenAt'] = $now; break; }
    if ($userId === null) {
        $userId = rr_id('usr');
        $db['users'][] = ['id' => $userId, 'email' => $email, 'displayName' => '', 'role' => $email === strtolower(RR_ADMIN_EMAIL) ? 'admin' : 'user', 'enabled' => true, 'accessStatus' => 'ACTIVE', 'syncPolicy' => 'EVERY_STOP', 'createdAt' => $now, 'lastSeenAt' => $now];
    }
    $role = 'user'; foreach ($db['users'] as $user) if ($user['id'] === $userId) { $role = $user['role'] ?? 'user'; break; }
    $deviceId = null;
    if ($purpose === 'app') {
        $installationId = trim((string)($input['installationId'] ?? ''));
        if ($installationId === '') rr_json(['ok' => false, 'error' => 'Hiányzó telepítésazonosító.'], 422);
        foreach ($db['devices'] as &$device) if ($device['installationId'] === $installationId) {
            $deviceId = $device['id']; $device['userId'] = $userId; $device['lastSeenAt'] = $now; $device['appVersion'] = (string)($input['appVersion'] ?? ''); break;
        }
        if ($deviceId === null) {
            $deviceId = rr_id('dev');
            $db['devices'][] = ['id' => $deviceId, 'userId' => $userId, 'installationId' => $installationId, 'name' => trim((string)($input['deviceName'] ?? 'Android készülék')), 'appVersion' => (string)($input['appVersion'] ?? ''), 'enabled' => true, 'createdAt' => $now, 'lastSeenAt' => $now, 'lastSyncAt' => null];
        }
    }
    $db['sessions'][] = ['tokenHash' => hash('sha256', $token), 'userId' => $userId, 'deviceId' => $deviceId, 'role' => $role, 'createdAt' => $now, 'lastSeenAt' => $now, 'expiresAt' => $now + ($purpose === 'admin' ? 43200 : 31536000)];
    $syncPolicy = 'EVERY_STOP'; $accessStatus='ACTIVE'; $displayName=''; foreach ($db['users'] as $user) if ($user['id'] === $userId) { $syncPolicy = $user['syncPolicy'] ?? 'EVERY_STOP'; $accessStatus=rr_user_status($user); $displayName=$user['displayName']??''; }
    return ['userId' => $userId, 'deviceId' => $deviceId, 'role' => $role, 'accessStatus'=>$accessStatus, 'displayName'=>$displayName, 'syncPolicy' => $syncPolicy];
});
rr_json(['ok' => true, 'token' => $token] + $result);
