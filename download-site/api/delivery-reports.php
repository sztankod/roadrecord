<?php
declare(strict_types=1);

require_once __DIR__ . '/smtp.php';

function rr_report_defaults(): array {
    return [
        'enabled' => false,
        'recipients' => [],
        'sendTime' => '10:00',
        'intro' => 'Tisztelt Címzett! Az alábbi összesítés a RoadRecord rendszerben az adott kiszállítási naphoz rögzített visszaszállító-köteles megállókat tartalmazza.',
        'outro' => 'Üdvözlettel: RoadRecord',
        'createdAt' => time(),
    ];
}

function rr_report_config(array $db): array {
    return array_replace(rr_report_defaults(), is_array($db['deliveryReportConfig'] ?? null) ? $db['deliveryReportConfig'] : []);
}

function rr_h(string $value): string { return htmlspecialchars($value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'); }

function rr_latest_user_snapshots(array $db): array {
    $latest = [];
    foreach ($db['snapshots'] as $snapshot) {
        $userId = (string)($snapshot['userId'] ?? '');
        if ($userId !== '' && (!isset($latest[$userId]) || ($snapshot['updatedAt'] ?? 0) > ($latest[$userId]['updatedAt'] ?? 0))) $latest[$userId] = $snapshot;
    }
    return $latest;
}

function rr_delivery_dates(array $db, ?string $onlyUserId = null): array {
    $dates = [];
    foreach (rr_latest_user_snapshots($db) as $userId => $snapshot) {
        if ($onlyUserId !== null && $userId !== $onlyUserId) continue;
        foreach (($snapshot['data']['days'] ?? []) as $day) {
            $closed = false;
            foreach (($day['events'] ?? []) as $event) if (($event['type'] ?? '') === 'WORK_END') $closed = true;
            if ($closed && !empty($day['date'])) $dates[(string)$day['date']] = true;
        }
    }
    return array_keys($dates);
}

function rr_delivery_rows(array $db, string $date): array {
    $rows = [];
    foreach (rr_latest_user_snapshots($db) as $userId => $snapshot) {
        $data = $snapshot['data'] ?? [];
        $dayIds = [];
        foreach (($data['days'] ?? []) as $day) if (($day['date'] ?? '') === $date) {
            $closed = false; foreach (($day['events'] ?? []) as $event) if (($event['type'] ?? '') === 'WORK_END') $closed = true;
            if (!$closed) return [];
            $dayIds[(string)($day['id'] ?? '')] = true;
        }
        if (!$dayIds) continue;
        $places = []; foreach (($data['places'] ?? []) as $place) $places[(string)($place['id'] ?? '')] = $place;
        $user = current(array_filter($db['users'], fn(array $item): bool => ($item['id'] ?? '') === $userId)) ?: [];
        foreach (($data['visits'] ?? []) as $visit) {
            if (!isset($dayIds[(string)($visit['workDayId'] ?? '')])) continue;
            $place = $places[(string)($visit['placeId'] ?? '')] ?? null;
            if (!$place || !($place['requiresReturnPickup'] ?? false)) continue;
            foreach (($visit['returnPickupItems'] ?? []) as $item) {
                $rows[] = [
                    'stop' => (string)($place['name'] ?? 'Ismeretlen megálló'),
                    'address' => (string)($place['officialAddress'] ?? ''),
                    'supplier' => (string)($item['supplier'] ?? ''),
                    'fulfillmentDate' => (string)($item['fulfillmentDate'] ?? $date),
                    'status' => (string)($item['status'] ?? ''),
                    'note' => (string)($item['note'] ?? ''),
                    'recordedBy' => (string)($user['displayName'] ?? '') ?: (string)($user['email'] ?? ''),
                ];
            }
        }
    }
    usort($rows, fn(array $a, array $b): int => [$a['supplier'],$a['stop']] <=> [$b['supplier'],$b['stop']]);
    return $rows;
}

function rr_delivery_html(array $config, string $date, array $rows): string {
    $yes = count(array_filter($rows, fn(array $row): bool => $row['status'] === 'YES'));
    $no = count(array_filter($rows, fn(array $row): bool => $row['status'] === 'NO'));
    $body = '<!doctype html><html lang="hu"><head><meta charset="utf-8"></head><body style="margin:0;background:#f3f6fa;font-family:Arial,sans-serif;color:#102b4e"><div style="max-width:900px;margin:auto;padding:24px"><div style="background:#0b2d5c;color:white;padding:20px;border-radius:14px 14px 0 0"><h1 style="margin:0">RoadRecord szállítólevél-jelentés</h1><p style="margin:7px 0 0">Kiszállítási nap: '.rr_h($date).'</p></div><div style="background:white;padding:22px;border:1px solid #d8e1eb"><p style="white-space:pre-line">'.nl2br(rr_h($config['intro'])).'</p><p><strong>Összes tétel: '.count($rows).'</strong> · Volt visszaszállító: '.$yes.' · Nem volt: '.$no.'</p><div style="overflow-x:auto"><table style="width:100%;border-collapse:collapse"><thead><tr style="background:#eef5fb"><th style="padding:9px;text-align:left">Megálló</th><th style="padding:9px;text-align:left">Szállító</th><th style="padding:9px;text-align:left">Teljesítés</th><th style="padding:9px;text-align:left">Visszaszállító</th><th style="padding:9px;text-align:left">Megjegyzés</th></tr></thead><tbody>';
    foreach ($rows as $row) $body .= '<tr><td style="padding:9px;border-top:1px solid #e3e9ef"><strong>'.rr_h($row['stop']).'</strong><br><small>'.rr_h($row['address']).'</small></td><td style="padding:9px;border-top:1px solid #e3e9ef">'.rr_h($row['supplier']).'</td><td style="padding:9px;border-top:1px solid #e3e9ef">'.rr_h($row['fulfillmentDate']).'</td><td style="padding:9px;border-top:1px solid #e3e9ef;font-weight:bold;color:'.($row['status']==='YES'?'#18863b':'#b42318').'">'.($row['status']==='YES'?'Igen':($row['status']==='NO'?'Nem':'Nincs válasz')).'</td><td style="padding:9px;border-top:1px solid #e3e9ef">'.rr_h($row['note']).'</td></tr>';
    return $body.'</tbody></table></div><p style="margin-top:22px;white-space:pre-line">'.nl2br(rr_h($config['outro'])).'</p></div></div></body></html>';
}

function rr_process_delivery_reports(?string $onlyUserId = null): array {
    $sent = [];
    $candidates = rr_transaction(fn(array &$db): array => ['config'=>rr_report_config($db),'dates'=>rr_delivery_dates($db,$onlyUserId),'sent'=>$db['sentDeliveryReports'] ?? []]);
    $config = $candidates['config'];
    if (!($config['enabled'] ?? false) || empty($config['recipients'])) return [];
    $timezone = new DateTimeZone('Europe/Budapest'); $now = new DateTimeImmutable('now',$timezone);
    foreach ($candidates['dates'] as $date) {
        $key = 'daily|'.$date; $existing=$candidates['sent'][$key]??null;if(is_array($existing)&&(!empty($existing['sentAt'])||($existing['sendingAt']??0)>time()-600))continue;
        $scheduled = DateTimeImmutable::createFromFormat('!Y-m-d H:i', $date.' '.$config['sendTime'], $timezone); if (!$scheduled || $now < $scheduled) continue;
        $rows = rr_transaction(fn(array &$db): array => rr_delivery_rows($db,$date)); if (!$rows) continue;
        $reserved=rr_transaction(function(array &$db)use($key):bool{$existing=$db['sentDeliveryReports'][$key]??null;if(is_array($existing)&&(!empty($existing['sentAt'])||($existing['sendingAt']??0)>time()-600))return false;$db['sentDeliveryReports'][$key]=['sendingAt'=>time()];return true;});if(!$reserved)continue;
        try {
            rr_send_mail($config['recipients'], 'RoadRecord szállítólevél-jelentés – '.$date, rr_delivery_html($config,$date,$rows));
            rr_transaction(function(array &$db)use($key,$date,$rows):void{$db['sentDeliveryReports'][$key]=['sentAt'=>time(),'date'=>$date,'items'=>count($rows)];$db['sentDeliveryReports']=array_slice($db['sentDeliveryReports'],-400,null,true);}); $sent[]=$date;
        } catch (Throwable $error) {
            rr_transaction(function(array &$db)use($key):void{unset($db['sentDeliveryReports'][$key]);});
            throw $error;
        }
    }
    return $sent;
}
