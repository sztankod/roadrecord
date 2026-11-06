<?php
declare(strict_types=1);
require_once dirname(__DIR__) . '/api/bootstrap.php';
$session = rr_session(true);
$action = $_GET['action'] ?? 'users';

if ($action === 'users' && $_SERVER['REQUEST_METHOD'] === 'GET') {
    $result = rr_transaction(function (array &$db): array {
        return array_map(function (array $user) use ($db): array {
            $devices = array_values(array_filter($db['devices'], fn(array $device): bool => $device['userId'] === $user['id']));
            $lastSync = 0;
            foreach ($devices as $device) $lastSync = max($lastSync, (int)($device['lastSyncAt'] ?? 0));
            return ['id' => $user['id'], 'email' => $user['email'], 'enabled' => $user['enabled'], 'role' => $user['role'], 'createdAt' => $user['createdAt'], 'lastSeenAt' => $user['lastSeenAt'], 'lastSyncAt' => $lastSync ?: null, 'deviceCount' => count($devices), 'activeDeviceCount' => count(array_filter($devices, fn(array $d): bool => $d['enabled']))];
        }, $db['users']);
    });
    usort($result, fn(array $a, array $b): int => ($b['lastSeenAt'] ?? 0) <=> ($a['lastSeenAt'] ?? 0));
    rr_json(['ok' => true, 'users' => $result]);
}

if ($action === 'detail' && $_SERVER['REQUEST_METHOD'] === 'GET') {
    $userId = trim((string)($_GET['userId'] ?? ''));
    $result = rr_transaction(function (array &$db) use ($userId): array {
        $user = current(array_filter($db['users'], fn(array $item): bool => $item['id'] === $userId)) ?: null;
        if (!$user) rr_json(['ok' => false, 'error' => 'A felhasználó nem található.'], 404);
        $devices = array_values(array_filter($db['devices'], fn(array $device): bool => $device['userId'] === $userId));
        $snapshots = [];
        foreach ($devices as $device) if (isset($db['snapshots'][$device['id']])) $snapshots[] = $db['snapshots'][$device['id']];
        usort($snapshots, fn(array $a, array $b): int => $b['updatedAt'] <=> $a['updatedAt']);
        return ['user' => $user, 'devices' => $devices, 'snapshot' => $snapshots[0] ?? null];
    });
    rr_json(['ok' => true] + $result);
}

if ($action === 'toggle' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    $input = rr_input();
    $type = ($input['type'] ?? '') === 'user' ? 'user' : 'device';
    $id = trim((string)($input['id'] ?? ''));
    $enabled = (bool)($input['enabled'] ?? false);
    rr_transaction(function (array &$db) use ($type, $id, $enabled, $session): void {
        $collection = $type === 'user' ? 'users' : 'devices';
        $found = false;
        foreach ($db[$collection] as &$item) if ($item['id'] === $id) { $item['enabled'] = $enabled; $found = true; break; }
        if (!$found) rr_json(['ok' => false, 'error' => 'A bejegyzés nem található.'], 404);
        $db['audit'][] = ['at' => time(), 'adminUserId' => $session['userId'], 'action' => $enabled ? 'enable' : 'disable', 'targetType' => $type, 'targetId' => $id];
        $db['audit'] = array_slice($db['audit'], -1000);
    });
    rr_json(['ok' => true]);
}

rr_json(['ok' => false, 'error' => 'Ismeretlen művelet.'], 404);
