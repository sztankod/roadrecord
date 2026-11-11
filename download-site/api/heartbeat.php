<?php
declare(strict_types=1);
require_once __DIR__ . '/bootstrap.php';
if ($_SERVER['REQUEST_METHOD'] !== 'POST') rr_json(['ok' => false], 405);
$session = rr_session(); $input = rr_input(); $now = time();
$allowed = ['appVersion','workOpen','workDayId','workDate','tripOpen','workStartedAt','tripStartedAt','completedStops','totalStops','distanceMeters','currentPlaceName','currentPlaceArrivedAt','nextPlaceName','nextPlaceAddress'];
$live = []; foreach ($allowed as $key) if (array_key_exists($key, $input)) $live[$key] = $input[$key];
rr_transaction(function (array &$db) use ($session, $now, $live): void {
 foreach ($db['devices'] as &$device) if ($device['id'] === ($session['deviceId'] ?? null)) { if (!$device['enabled']) rr_json(['ok'=>false,'error'=>'Ez a készülék le van tiltva.'],403); $device['lastSeenAt']=$now; $device['lastHeartbeatAt']=$now; $device['liveState']=$live; break; }
 foreach ($db['users'] as &$user) if ($user['id'] === $session['userId']) { if (rr_user_status($user)==='BLOCKED') rr_json(['ok'=>false,'error'=>'Ez a felhasználó le van tiltva.'],403); $user['lastSeenAt']=$now; break; }
});
rr_json(['ok'=>true,'receivedAt'=>$now]);
