<?php
declare(strict_types=1);
require_once __DIR__ . '/bootstrap.php';
require_once __DIR__ . '/delivery-reports.php';
if ($_SERVER['REQUEST_METHOD'] !== 'POST') rr_json(['ok' => false], 405);
$session = rr_session();
$input = rr_input();
$snapshot = $input['snapshot'] ?? null;
if (!is_array($snapshot)) rr_json(['ok' => false, 'error' => 'Hiányzó szinkronizálási adat.'], 422);
$encoded = json_encode($snapshot, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
if (strlen((string)$encoded) > 8 * 1024 * 1024) rr_json(['ok' => false, 'error' => 'A szinkronizálási csomag túl nagy.'], 413);
$now = time();
$overrides=rr_transaction(function (array &$db) use ($session, $snapshot, $now): array {
    $user = current(array_filter($db['users'], fn(array $u): bool => $u['id'] === $session['userId'])) ?: null;
    $device = current(array_filter($db['devices'], fn(array $d): bool => $d['id'] === ($session['deviceId'] ?? null))) ?: null;
    if (!$user || !$device || rr_user_status($user) === 'BLOCKED' || !$device['enabled']) rr_json(['ok' => false, 'error' => 'Ez a példány le van tiltva.'], 403);
    $applied=$db['deliveryNoteOverrides'][$user['id']]??[];
    foreach($snapshot['visits'] as &$visit){$visitId=(string)($visit['id']??'');if(array_key_exists($visitId,$applied)){$visit['returnPickupItems']=array_values($applied[$visitId]);$visit['returnPickupStatus']=$applied[$visitId]?'COMPLETE':null;}}unset($visit);
    $db['snapshots'][$device['id']] = ['userId' => $user['id'], 'deviceId' => $device['id'], 'updatedAt' => $now, 'data' => $snapshot];
    if($applied)unset($db['deliveryNoteOverrides'][$user['id']]);
    foreach ($db['devices'] as &$item) if ($item['id'] === $device['id']) { $item['lastSeenAt'] = $now; $item['lastSyncAt'] = $now; }
    return $applied;
});
$reportError = null;
try { rr_process_delivery_reports($session['userId']); } catch (Throwable $error) { $reportError = $error->getMessage(); }
rr_json(['ok' => true, 'syncedAt' => $now, 'reportError' => $reportError, 'deliveryNoteOverrides'=>$overrides]);
