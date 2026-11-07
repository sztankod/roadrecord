<?php
declare(strict_types=1);
require_once __DIR__ . '/bootstrap.php';
$session = rr_session();
$result = rr_transaction(function (array &$db) use ($session): array {
    $user = current(array_filter($db['users'], fn(array $u): bool => $u['id'] === $session['userId'])) ?: null;
    $device = current(array_filter($db['devices'], fn(array $d): bool => $d['id'] === ($session['deviceId'] ?? null))) ?: null;
    if ($user) foreach ($db['users'] as &$item) if ($item['id'] === $user['id']) $item['lastSeenAt'] = time();
    if ($device) foreach ($db['devices'] as &$item) if ($item['id'] === $device['id']) $item['lastSeenAt'] = time();
    return ['allowed' => ($user['enabled'] ?? false) && ($device['enabled'] ?? false), 'email' => $user['email'] ?? '', 'deviceId' => $device['id'] ?? null, 'syncPolicy' => $user['syncPolicy'] ?? 'APP_ACTIVITY'];
});
rr_json(['ok' => true] + $result);
