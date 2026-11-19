<?php
declare(strict_types=1);

require_once __DIR__ . '/smtp.php';

function rr_report_defaults(): array {
    return [
        'enabled' => false,
        'recipients' => [],
        'sendTime' => '10:00',
        'subject' => 'RoadRecord szállítólevél riport – {date}',
        'heading' => 'RoadRecord szállítólevél riport',
        'testRecipient' => '',
        'intro' => 'Tisztelt Címzett! Az alábbi összesítés a RoadRecord rendszerben az adott kiszállítási naphoz rögzített visszaszállító-köteles megállókat tartalmazza.',
        'outro' => 'Üdvözlettel: RoadRecord',
        'createdAt' => time(),
    ];
}

function rr_report_config(array $db): array {
    return array_replace(rr_report_defaults(), is_array($db['deliveryReportConfig'] ?? null) ? $db['deliveryReportConfig'] : []);
}

function rr_h(string $value): string { return htmlspecialchars($value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'); }

function rr_report_subject(array $config, string $date, bool $test = false): string {
    $template = trim((string)($config['subject'] ?? '')) ?: 'RoadRecord szállítólevél riport – {date}';
    $subject = str_replace('{date}', $date, $template);
    return $test ? $subject.' – TESZT' : $subject;
}

function rr_latest_user_snapshots(array $db): array {
    $latest = [];
    foreach ($db['snapshots'] as $snapshot) {
        $userId = (string)($snapshot['userId'] ?? '');
        if ($userId !== '' && (!isset($latest[$userId]) || ($snapshot['updatedAt'] ?? 0) > ($latest[$userId]['updatedAt'] ?? 0))) $latest[$userId] = $snapshot;
    }
    return $latest;
}

function rr_work_completion_date(array $day): ?string {
    $end = 0; foreach (($day['events'] ?? []) as $event) if (($event['type'] ?? '') === 'WORK_END') $end=max($end,(int)($event['timestamp']??0));
    return $end>0?(new DateTimeImmutable('@'.intdiv($end,1000)))->setTimezone(new DateTimeZone('Europe/Budapest'))->format('Y-m-d'):null;
}

function rr_delivery_dates(array $db, ?string $onlyUserId = null): array {
    $dates = [];
    foreach (rr_latest_user_snapshots($db) as $userId => $snapshot) {
        if ($onlyUserId !== null && $userId !== $onlyUserId) continue;
        foreach (($snapshot['data']['days'] ?? []) as $day) {
            $completionDate=rr_work_completion_date($day);if($completionDate!==null)$dates[$completionDate]=true;
        }
    }
    return array_keys($dates);
}

function rr_delivery_rows(array $db, string $date): array {
    $grouped = [];
    foreach (rr_latest_user_snapshots($db) as $userId => $snapshot) {
        $data = $snapshot['data'] ?? [];
        $dayIds = [];
        foreach (($data['days'] ?? []) as $day) if (rr_work_completion_date($day)===$date)$dayIds[(string)($day['id'] ?? '')]=true;
        $places = []; foreach (($data['places'] ?? []) as $place) $places[(string)($place['id'] ?? '')] = $place;
        $user = current(array_filter($db['users'], fn(array $item): bool => ($item['id'] ?? '') === $userId)) ?: [];
        $courier = (string)($user['displayName'] ?? '') ?: (string)($user['email'] ?? '');
        $notesByPlace = [];
        foreach (($data['visits'] ?? []) as $visit) {
            $placeId=(string)($visit['placeId']??'');
            foreach (($visit['returnPickupItems'] ?? []) as $item) {
                $recordedAt=(int)($item['recordedAt']??0);
                if($recordedAt<=0)continue;
                $recordedDate=(new DateTimeImmutable('@'.intdiv($recordedAt,1000)))->setTimezone(new DateTimeZone('Europe/Budapest'))->format('Y-m-d');
                if($recordedDate!==$date)continue;
                $notesByPlace[$placeId][]=['fulfillmentDate'=>(string)($item['fulfillmentDate']??''),'note'=>(string)($item['note']??''),'recordedBy'=>$courier];
            }
        }
        $visitedPlaces=[];
        foreach (($data['visits'] ?? []) as $visit) {
            if (!isset($dayIds[(string)($visit['workDayId'] ?? '')])) continue;
            $placeId=(string)($visit['placeId']??'');$place=$places[$placeId]??null;
            if (!$place || !($place['requiresReturnPickup'] ?? false) || isset($visitedPlaces[$placeId])) continue;
            $visitedPlaces[$placeId]=true;$stop=(string)($place['name']??'Ismeretlen megálló');$key=strtolower(trim($stop));
            if(!isset($grouped[$key]))$grouped[$key]=['stop'=>$stop,'couriers'=>[],'items'=>[]];
            if($courier!==''&&!in_array($courier,$grouped[$key]['couriers'],true))$grouped[$key]['couriers'][]=$courier;
            foreach(($notesByPlace[$placeId]??[])as$item)$grouped[$key]['items'][]=$item;
        }
        foreach($notesByPlace as $placeId=>$items){
            if(isset($visitedPlaces[$placeId]))continue;
            $place=$places[$placeId]??null;if(!$place||!($place['requiresReturnPickup']??false))continue;
            $stop=(string)($place['name']??'Ismeretlen megálló');$key=strtolower(trim($stop));
            if(!isset($grouped[$key]))$grouped[$key]=['stop'=>$stop,'couriers'=>[],'items'=>[]];
            if($courier!==''&&!in_array($courier,$grouped[$key]['couriers'],true))$grouped[$key]['couriers'][]=$courier;
            foreach($items as$item)$grouped[$key]['items'][]=$item;
        }
    }
    $rows=array_values($grouped);usort($rows,fn(array$a,array$b):int=>strcasecmp($a['stop'],$b['stop']));
    return $rows;
}

function rr_report_text(string $value): string {
    $escaped=rr_h($value);
    $linked=preg_replace_callback('~https?://[^\s<]+~i',fn(array$match):string=>'<a href="'.$match[0].'" style="color:#1479d4">'.$match[0].'</a>',$escaped);
    return nl2br($linked??$escaped);
}

function rr_short_report_date(string $value): string {
    $date=DateTimeImmutable::createFromFormat('!Y-m-d',$value);
    return $date?$date->format('m.d'):$value;
}

function rr_delivery_html(array $config, string $date, array $rows): string {
    $heading = trim((string)($config['heading'] ?? '')) ?: 'RoadRecord szállítólevél riport';
    $logo='http://roadrecord.widor.nhely.hu/admin/roadrecord-mark-white.png';
    $body = '<!doctype html><html lang="hu"><head><meta charset="utf-8"></head><body style="margin:0;background:#f3f6fa;font-family:Arial,sans-serif;color:#102b4e"><div style="max-width:900px;margin:auto;padding:24px"><div style="background:#0b2d5c;color:white;padding:20px;border-radius:14px 14px 0 0"><table role="presentation" cellspacing="0" cellpadding="0" border="0"><tr><td style="padding-right:12px"><img src="'.$logo.'" alt="RoadRecord" width="48" height="48" style="display:block;border:0;outline:0;box-shadow:none"></td><td><h1 style="margin:0">'.rr_h($heading).'</h1></td></tr></table><p style="margin:7px 0 0">Kiszállítási nap: '.rr_h($date).'</p></div><div style="background:white;padding:22px;border:1px solid #d8e1eb"><p>'.rr_report_text((string)$config['intro']).'</p><p><strong>Összes megálló: '.count($rows).'</strong></p><div style="overflow-x:auto"><table style="width:100%;border-collapse:collapse"><thead><tr style="background:#eef5fb"><th style="padding:9px;text-align:left">Megálló</th><th style="padding:9px;text-align:left">Teljesítési dátum</th><th style="padding:9px;text-align:left">Futár</th><th style="padding:9px;text-align:left">Megjegyzés</th></tr></thead><tbody>';
    foreach ($rows as $row) {$items=$row['items']??[];$dates=$items?implode('<br>',array_map(fn(array$item):string=>rr_h(rr_short_report_date((string)$item['fulfillmentDate'])),$items)):'<strong style="color:#c83c3c">✕</strong>';$notes=$items?implode('<br>',array_filter(array_map(fn(array$item):string=>rr_h((string)($item['note']??'')),$items))):'';$couriers=implode('<br>',array_map('rr_h',$row['couriers']??[]));$body.='<tr><td style="padding:9px;border-top:1px solid #e3e9ef"><strong>'.rr_h($row['stop']).'</strong></td><td style="padding:9px;border-top:1px solid #e3e9ef">'.$dates.'</td><td style="padding:9px;border-top:1px solid #e3e9ef">'.$couriers.'</td><td style="padding:9px;border-top:1px solid #e3e9ef">'.$notes.'</td></tr>';}
    return $body.'</tbody></table></div><p style="margin-top:22px">'.rr_report_text((string)$config['outro']).'</p></div></div></body></html>';
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
            rr_send_mail($config['recipients'], rr_report_subject($config,$date), rr_delivery_html($config,$date,$rows));
            rr_transaction(function(array &$db)use($key,$date,$rows):void{$db['sentDeliveryReports'][$key]=['sentAt'=>time(),'date'=>$date,'items'=>count($rows)];$db['sentDeliveryReports']=array_slice($db['sentDeliveryReports'],-400,null,true);}); $sent[]=$date;
        } catch (Throwable $error) {
            rr_transaction(function(array &$db)use($key):void{unset($db['sentDeliveryReports'][$key]);});
            throw $error;
        }
    }
    return $sent;
}
