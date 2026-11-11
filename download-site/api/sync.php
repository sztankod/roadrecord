<?php
declare(strict_types=1);
require_once __DIR__ . '/bootstrap.php';
if ($_SERVER['REQUEST_METHOD'] !== 'POST') rr_json(['ok' => false], 405);
$session = rr_session();
$input = rr_input();
$snapshot = $input['snapshot'] ?? null;
if (!is_array($snapshot)) rr_json(['ok' => false, 'error' => 'Hiányzó szinkronizálási adat.'], 422);
$encoded = json_encode($snapshot, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
if (strlen((string)$encoded) > 8 * 1024 * 1024) rr_json(['ok' => false, 'error' => 'A szinkronizálási csomag túl nagy.'], 413);
$now = time();
rr_transaction(function (array &$db) use ($session, $snapshot, $now): void {
    $user = current(array_filter($db['users'], fn(array $u): bool => $u['id'] === $session['userId'])) ?: null;
    $device = current(array_filter($db['devices'], fn(array $d): bool => $d['id'] === ($session['deviceId'] ?? null))) ?: null;
    if (!$user || !$device || rr_user_status($user) === 'BLOCKED' || !$device['enabled']) rr_json(['ok' => false, 'error' => 'Ez a példány le van tiltva.'], 403);
    $db['snapshots'][$device['id']] = ['userId' => $user['id'], 'deviceId' => $device['id'], 'updatedAt' => $now, 'data' => $snapshot];
    foreach ($db['devices'] as &$item) if ($item['id'] === $device['id']) { $item['lastSeenAt'] = $now; $item['lastSyncAt'] = $now; }
});
rr_json(['ok' => true, 'syncedAt' => $now]);
