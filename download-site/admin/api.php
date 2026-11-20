<?php
declare(strict_types=1);
require_once dirname(__DIR__) . '/api/bootstrap.php';
require_once dirname(__DIR__) . '/api/delivery-reports.php';
$session = rr_session(true);
$action = $_GET['action'] ?? 'users';
$adminRole = rr_admin_role($session);
$guestAllowed = $action === 'session' || $action === 'session-keepalive' || ($action === 'delivery-notes' && $_SERVER['REQUEST_METHOD'] === 'GET') || ($action === 'delivery-note-summary' && $_SERVER['REQUEST_METHOD'] === 'GET');
if ($adminRole === 'guest' && !$guestAllowed) rr_json(['ok'=>false,'error'=>'A guest hozzáférés csak megtekintésre jogosult.'],403);

if ($action === 'session' && $_SERVER['REQUEST_METHOD'] === 'GET') {
    rr_json(['ok'=>true,'role'=>$adminRole]);
}

if ($action === 'session-keepalive' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    $tokenHash=(string)($session['tokenHash']??'');$now=time();
    rr_transaction(function(array &$db)use($tokenHash,$now):void{foreach($db['sessions']as&$item)if(hash_equals((string)($item['tokenHash']??''),$tokenHash)){$item['lastSeenAt']=$now;$item['expiresAt']=$now+300;break;}unset($item);});
    rr_json(['ok'=>true,'expiresAt'=>$now+300]);
}

if ($action === 'admin-login-log' && $_SERVER['REQUEST_METHOD'] === 'GET') {
    $rows=rr_read(fn(array $db):array=>array_values(array_reverse(array_slice($db['adminLoginLog']??[],-500))));
    rr_json(['ok'=>true,'rows'=>$rows]);
}

if ($action === 'admin-accounts' && $_SERVER['REQUEST_METHOD'] === 'GET') {
    $accounts=rr_read(fn(array $db):array=>array_map(fn(array $account):array=>['id'=>$account['id'],'name'=>$account['name']??'','email'=>$account['email']??'','role'=>$account['role']??'guest','enabled'=>$account['enabled']??true,'createdAt'=>$account['createdAt']??null,'lastSeenAt'=>$account['lastSeenAt']??null],$db['adminAccounts']??[]));
    usort($accounts,fn(array$a,array$b):int=>strcasecmp((string)$a['name'],(string)$b['name']));rr_json(['ok'=>true,'accounts'=>$accounts]);
}

if ($action === 'admin-accounts' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    $input=rr_input();$id=trim((string)($input['id']??''));$name=trim((string)($input['name']??''));$email=rr_email($input['email']??'');$password=(string)($input['password']??'');$role=in_array(($input['role']??''),['admin','guest'],true)?(string)$input['role']:'guest';$enabled=(bool)($input['enabled']??true);
    if($name==='')rr_json(['ok'=>false,'error'=>'A név megadása kötelező.'],422);if($id===''&&strlen($password)<8)rr_json(['ok'=>false,'error'=>'Az új jelszó legalább 8 karakter legyen.'],422);if($id!==''&&$password!==''&&strlen($password)<8)rr_json(['ok'=>false,'error'=>'Az új jelszó legalább 8 karakter legyen.'],422);
    $saved=rr_transaction(function(array &$db)use($id,$name,$email,$password,$role,$enabled,$session):array{$accounts=&$db['adminAccounts'];foreach($accounts as$account)if(($account['email']??'')===$email&&($account['id']??'')!==$id)rr_json(['ok'=>false,'error'=>'Ezzel az e-mail-címmel már létezik hozzáférés.'],409);$index=null;foreach($accounts as$i=>$account)if(($account['id']??'')===$id){$index=$i;break;}if($index===null){$account=['id'=>rr_id('adm'),'name'=>$name,'email'=>$email,'passwordHash'=>password_hash($password,PASSWORD_DEFAULT),'role'=>$role,'enabled'=>$enabled,'createdAt'=>time(),'lastSeenAt'=>null];$accounts[]=$account;}else{$accounts[$index]['name']=$name;$accounts[$index]['email']=$email;$accounts[$index]['role']=$role;$accounts[$index]['enabled']=$enabled;if($password!=='')$accounts[$index]['passwordHash']=password_hash($password,PASSWORD_DEFAULT);$account=$accounts[$index];}$db['audit'][]=['at'=>time(),'adminUserId'=>$session['userId'],'action'=>'admin-account-save','targetType'=>'adminAccount','targetId'=>$account['id'],'value'=>['email'=>$email,'role'=>$role,'enabled'=>$enabled]];return['id'=>$account['id'],'name'=>$name,'email'=>$email,'role'=>$role,'enabled'=>$enabled];});rr_json(['ok'=>true,'account'=>$saved]);
}

if ($action === 'admin-accounts' && $_SERVER['REQUEST_METHOD'] === 'DELETE') {
    $input=rr_input();$id=trim((string)($input['id']??''));if($id==='')rr_json(['ok'=>false,'error'=>'Hiányzó azonosító.'],422);
    rr_transaction(function(array &$db)use($id,$session):void{$before=count($db['adminAccounts']??[]);$db['adminAccounts']=array_values(array_filter($db['adminAccounts']??[],fn(array$a):bool=>($a['id']??'')!==$id));if(count($db['adminAccounts'])===$before)rr_json(['ok'=>false,'error'=>'A hozzáférés nem található.'],404);$db['sessions']=array_values(array_filter($db['sessions'],fn(array$s):bool=>($s['adminAccountId']??'')!==$id));$db['audit'][]=['at'=>time(),'adminUserId'=>$session['userId'],'action'=>'admin-account-delete','targetType'=>'adminAccount','targetId'=>$id];});rr_json(['ok'=>true]);
}

if ($action === 'users' && $_SERVER['REQUEST_METHOD'] === 'GET') {
    $result = rr_read(function (array $db): array {
        return array_map(function (array $user) use ($db): array {
            $devices = array_values(array_filter($db['devices'], fn(array $device): bool => $device['userId'] === $user['id']));
            $lastSync = 0;
            foreach ($devices as $device) $lastSync = max($lastSync, (int)($device['lastSyncAt'] ?? 0));
            $online = count(array_filter($devices, fn(array $d): bool => (int)($d['lastHeartbeatAt'] ?? 0) >= time()-120));
            $openWork = count(array_filter($devices, fn(array $d): bool => (int)($d['lastHeartbeatAt'] ?? 0) >= time()-120 && (bool)($d['liveState']['workOpen'] ?? false)));
            return ['id' => $user['id'], 'email' => $user['email'], 'displayName'=>$user['displayName']??'', 'accessStatus'=>rr_user_status($user), 'enabled' => rr_user_status($user)!=='BLOCKED', 'role' => $user['role']??'user', 'syncPolicy' => $user['syncPolicy'] ?? 'EVERY_STOP', 'createdAt' => $user['createdAt'], 'lastSeenAt' => $user['lastSeenAt'], 'lastSyncAt' => $lastSync ?: null, 'deviceCount' => count($devices), 'activeDeviceCount' => count(array_filter($devices, fn(array $d): bool => $d['enabled'])), 'onlineDeviceCount'=>$online, 'openWorkCount'=>$openWork];
        }, $db['users']);
    });
    usort($result, fn(array $a, array $b): int => ($b['lastSeenAt'] ?? 0) <=> ($a['lastSeenAt'] ?? 0));
    rr_json(['ok' => true, 'users' => $result]);
}

if ($action === 'detail' && $_SERVER['REQUEST_METHOD'] === 'GET') {
    $userId = trim((string)($_GET['userId'] ?? ''));
    $result = rr_read(function (array $db) use ($userId): array {
        $user = current(array_filter($db['users'], fn(array $item): bool => $item['id'] === $userId)) ?: null;
        if (!$user) rr_json(['ok' => false, 'error' => 'A felhasználó nem található.'], 404);
        $devices = array_values(array_filter($db['devices'], fn(array $device): bool => $device['userId'] === $userId));
        $snapshots = [];
        foreach ($devices as $device) if (isset($db['snapshots'][$device['id']])) $snapshots[] = $db['snapshots'][$device['id']];
        usort($snapshots, fn(array $a, array $b): int => $b['updatedAt'] <=> $a['updatedAt']);
        $user['displayName']=$user['displayName']??'';$user['accessStatus']=rr_user_status($user);$user['role']=$user['role']??'user';$user['syncPolicy']=$user['syncPolicy']??'EVERY_STOP';
        return ['user' => $user, 'devices' => $devices, 'snapshot' => $snapshots[0] ?? null];
    });
    rr_json(['ok' => true] + $result);
}

if ($action === 'central-stops' && $_SERVER['REQUEST_METHOD'] === 'GET') {
    $result = rr_read(fn(array $db): array => [
        'stops' => array_values($db['centralStops'] ?? []),
        'updatedAt' => $db['centralStopsUpdatedAt'] ?? null,
    ]);
    rr_json(['ok' => true] + $result);
}

if ($action === 'delivery-report-settings' && $_SERVER['REQUEST_METHOD'] === 'GET') {
    $config=rr_read(fn(array $db):array=>rr_report_config($db));
    rr_json(['ok'=>true,'config'=>$config]);
}

if ($action === 'delivery-notes' && $_SERVER['REQUEST_METHOD'] === 'GET') {
    $rows=rr_transaction(function(array &$db):array{$result=[];$tz=new DateTimeZone('Europe/Budapest');$legacyRecordedAt=1794960000000;foreach(rr_latest_user_snapshots($db) as $userId=>$snapshot){$data=$snapshot['data']??[];$user=current(array_filter($db['users'],fn(array $u):bool=>($u['id']??'')===$userId))?:[];$name=(string)($user['displayName']??'')?:((string)($user['email']??''));$places=[];foreach(($data['places']??[])as$p)$places[(string)($p['id']??'')]=$p;$days=[];foreach(($data['days']??[])as$day)$days[(string)($day['id']??'')]=(string)($day['date']??'');foreach(($data['visits']??[])as$visit){$dayId=(string)($visit['workDayId']??'');$place=$places[(string)($visit['placeId']??'')]??null;if(!$place||!($place['requiresReturnPickup']??false))continue;$arrival=(int)($visit['arrivalTime']??0);$visitDate=$arrival>0?(new DateTimeImmutable('@'.intdiv($arrival,1000)))->setTimezone($tz)->format('Y-m-d'):($days[$dayId]??'');$items=array_values($visit['returnPickupItems']??[]);$groups=[];foreach($items as$itemIndex=>$item){$item['_index']=$itemIndex;$recordedAt=(int)($item['recordedAt']??$legacyRecordedAt);$collectionDate=(string)($item['collectionDate']??'');if($collectionDate==='')$collectionDate=(new DateTimeImmutable('@'.intdiv($recordedAt,1000)))->setTimezone($tz)->format('Y-m-d');$groups[$collectionDate][]=$item;}if(!$groups&&$visitDate!=='')$groups[$visitDate]=[];foreach($groups as$collectionDate=>$groupItems)$result[]=['userId'=>$userId,'deviceId'=>$snapshot['deviceId']??'','visitId'=>(string)($visit['id']??''),'completionDate'=>$collectionDate,'stop'=>(string)($place['name']??'Ismeretlen megálló'),'address'=>(string)($place['officialAddress']??''),'driver'=>$name,'items'=>$groupItems];}}usort($result,fn(array$a,array$b):int=>[$b['completionDate'],$a['stop'],$a['driver']]<=>[$a['completionDate'],$b['stop'],$b['driver']]);return$result;});rr_json(['ok'=>true,'rows'=>$rows]);
}

if ($action === 'delivery-note-summary' && $_SERVER['REQUEST_METHOD'] === 'GET') {
    $rows=rr_transaction(function(array &$db):array{
        $result=[];$tz=new DateTimeZone('Europe/Budapest');
        foreach(rr_latest_user_snapshots($db) as $userId=>$snapshot){
            $data=$snapshot['data']??[];$user=current(array_filter($db['users'],fn(array $u):bool=>($u['id']??'')===$userId))?:[];$driver=(string)($user['displayName']??'')?:((string)($user['email']??''));
            $places=[];foreach(($data['places']??[])as$place)$places[(string)($place['id']??'')]=$place;
            $performanceDates=[];foreach(($data['days']??[])as$day){$workEnd=0;foreach(($day['events']??[])as$event)if(($event['type']??'')==='WORK_END')$workEnd=max($workEnd,(int)($event['timestamp']??0));if($workEnd>0)$performanceDates[(string)($day['id']??'')]=(new DateTimeImmutable('@'.intdiv($workEnd,1000)))->setTimezone($tz)->format('Y-m-d');}
            $deliveryNotes=[];
            foreach(($data['visits']??[])as$visit){
                $placeId=(string)($visit['placeId']??'');
                foreach(($visit['returnPickupItems']??[])as$item){
                    $fulfillmentDate=(string)($item['fulfillmentDate']??'');
                    if($placeId!==''&&$fulfillmentDate!=='')$deliveryNotes[$placeId.'|'.$fulfillmentDate]=true;
                }
            }
            $seen=[];
            foreach(($data['visits']??[])as$visit){
                $date=$performanceDates[(string)($visit['workDayId']??'')]??'';$placeId=(string)($visit['placeId']??'');$place=$places[$placeId]??null;
                if($date===''||!$place||!($place['requiresReturnPickup']??false))continue;
                $rowKey=$date.'|'.$placeId.'|'.$driver;if(isset($seen[$rowKey]))continue;$seen[$rowKey]=true;
                $result[]=['date'=>$date,'stop'=>(string)($place['name']??'Ismeretlen megálló'),'driver'=>$driver,'hasDeliveryNote'=>isset($deliveryNotes[$placeId.'|'.$date])];
            }
        }
        usort($result,fn(array$a,array$b):int=>[$b['date'],$a['stop'],$a['driver']]<=>[$a['date'],$b['stop'],$b['driver']]);return$result;
    });rr_json(['ok'=>true,'rows'=>$rows]);
}

if ($action === 'delivery-notes' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    $input=rr_input();$deviceId=(string)($input['deviceId']??'');$visitId=(string)($input['visitId']??'');$operation=(string)($input['operation']??'');$index=(int)($input['index']??-1);$date=(string)($input['fulfillmentDate']??'');$collectionDate=(string)($input['collectionDate']??'');$note=trim((string)($input['note']??''));if(!in_array($operation,['add','update','delete'],true)||$deviceId===''||$visitId==='')rr_json(['ok'=>false,'error'=>'Hiányos kérés.'],422);if($operation!=='delete'&&(!preg_match('/^\d{4}-\d{2}-\d{2}$/',$date)||($collectionDate!==''&&!preg_match('/^\d{4}-\d{2}-\d{2}$/',$collectionDate))))rr_json(['ok'=>false,'error'=>'Hibás dátum.'],422);
    rr_transaction(function(array &$db)use($deviceId,$visitId,$operation,$index,$date,$collectionDate,$note,$session):void{if(!isset($db['snapshots'][$deviceId]))rr_json(['ok'=>false,'error'=>'A szinkronizált adatok nem találhatók.'],404);$found=false;$items=[];foreach($db['snapshots'][$deviceId]['data']['visits']as&$visit){if((string)($visit['id']??'')!==$visitId)continue;$found=true;$items=array_values($visit['returnPickupItems']??[]);if($operation==='add')$items[]=['fulfillmentDate'=>$date,'note'=>$note,'recordedAt'=>time()*1000,'collectionDate'=>$collectionDate!==''?$collectionDate:date];elseif($index<0||$index>=count($items))rr_json(['ok'=>false,'error'=>'A szállítólevél nem található.'],404);elseif($operation==='update')$items[$index]=['fulfillmentDate'=>$date,'note'=>$note,'recordedAt'=>(int)($items[$index]['recordedAt']??1794960000000),'collectionDate'=>(string)($items[$index]['collectionDate']??$collectionDate)];else array_splice($items,$index,1);$visit['returnPickupItems']=$items;$visit['returnPickupStatus']=$items?'COMPLETE':null;break;}unset($visit);if(!$found)rr_json(['ok'=>false,'error'=>'A látogatás nem található.'],404);$userId=(string)($db['snapshots'][$deviceId]['userId']??'');$db['deliveryNoteOverrides'][$userId][$visitId]=$items;$db['audit'][]=['at'=>time(),'adminUserId'=>$session['userId'],'action'=>'delivery-note-'.$operation,'targetType'=>'visit','targetId'=>$visitId];});rr_json(['ok'=>true]);
}

if ($action === 'delivery-report-settings' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    $input=rr_input();$recipients=[];foreach(($input['recipients']??[]) as $email){$email=strtolower(trim((string)$email));if(filter_var($email,FILTER_VALIDATE_EMAIL))$recipients[]=$email;}
    $time=(string)($input['sendTime']??'10:00');if(!preg_match('/^(?:[01]\d|2[0-3]):[0-5]\d$/',$time))rr_json(['ok'=>false,'error'=>'Hibás küldési időpont.'],422);
    if(($input['enabled']??false)&&!$recipients)rr_json(['ok'=>false,'error'=>'Adj meg legalább egy címzettet.'],422);
    $subject=trim((string)($input['subject']??''));if($subject==='')rr_json(['ok'=>false,'error'=>'Az e-mail tárgya nem lehet üres.'],422);
    $heading=trim((string)($input['heading']??''));if($heading==='')rr_json(['ok'=>false,'error'=>'Az e-mail fejlécének szövege nem lehet üres.'],422);
    $testRecipient=strtolower(trim((string)($input['testRecipient']??'')));if($testRecipient!==''&&!filter_var($testRecipient,FILTER_VALIDATE_EMAIL))rr_json(['ok'=>false,'error'=>'Hibás tesztcímzett.'],422);
    $config=['enabled'=>(bool)($input['enabled']??false),'recipients'=>array_values(array_unique($recipients)),'sendTime'=>$time,'subject'=>$subject,'heading'=>$heading,'testRecipient'=>$testRecipient,'intro'=>trim((string)($input['intro']??'')),'outro'=>trim((string)($input['outro']??'')),'createdAt'=>(int)($input['createdAt']??time())];
    rr_transaction(function(array &$db)use($config,$session):void{$db['deliveryReportConfig']=$config;$db['audit'][]=['at'=>time(),'adminUserId'=>$session['userId'],'action'=>'delivery-report-settings','targetType'=>'report','targetId'=>'daily','value'=>['enabled'=>$config['enabled'],'recipients'=>count($config['recipients']),'sendTime'=>$config['sendTime']]];});
    rr_json(['ok'=>true,'config'=>$config]);
}

if ($action === 'delivery-report-test' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    $input=rr_input();$mode=(string)($input['mode']??'sample');$date=(string)($input['date']??date('Y-m-d'));
    if(!in_array($mode,['sample','live'],true))rr_json(['ok'=>false,'error'=>'Hibás tesztadattípus.'],422);
    if(!preg_match('/^\d{4}-\d{2}-\d{2}$/',$date))rr_json(['ok'=>false,'error'=>'Hibás riportdátum.'],422);
    $result=rr_transaction(function(array &$db)use($session,$mode,$date):array{
        $config=rr_report_config($db);$email='';
        if(isset($session['adminAccountId'])){foreach(($db['adminAccounts']??[])as$account)if(($account['id']??'')===$session['adminAccountId']){$email=(string)($account['email']??'');break;}}
        if($email===''){foreach(($db['users']??[])as$user)if(($user['id']??'')===$session['userId']){$email=(string)($user['email']??'');break;}}
        return['config'=>$config,'email'=>$email,'rows'=>$mode==='live'?rr_delivery_rows($db,$date):[]];
    });
    $recipient=strtolower(trim((string)($input['recipient']??'')))?:((string)($result['config']['testRecipient']??'')?:$result['email']);if(!filter_var($recipient,FILTER_VALIDATE_EMAIL))rr_json(['ok'=>false,'error'=>'Hibás tesztcímzett.'],422);
    $rows=$mode==='live'?$result['rows']:[['stop'=>'Minta megálló','couriers'=>['RoadRecord'],'items'=>[['fulfillmentDate'=>$date,'note'=>'Teszt riport','recordedBy'=>'RoadRecord']]]];
    if($mode==='live'&&!$rows)rr_json(['ok'=>false,'error'=>'A kiválasztott naphoz nincs kiküldhető éles adat.'],404);
    rr_send_mail([$recipient],rr_report_subject($result['config'],$date,true),rr_delivery_html($result['config'],$date,$rows));
    rr_json(['ok'=>true,'recipient'=>$recipient,'mode'=>$mode,'date'=>$date,'items'=>count($rows)]);
}

if ($action === 'central-import' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    $input = rr_input();
    $userId = trim((string)($input['userId'] ?? ''));
    $placeIds = array_values(array_unique(array_map('strval', is_array($input['placeIds'] ?? null) ? $input['placeIds'] : [])));
    if ($userId === '' || !$placeIds) rr_json(['ok'=>false,'error'=>'Jelölj ki legalább egy megállót.'],422);
    if (count($placeIds) > 1000) rr_json(['ok'=>false,'error'=>'Túl sok kijelölt megálló.'],413);
    $result = rr_transaction(function(array &$db) use($userId,$placeIds,$session):array {
        $user = current(array_filter($db['users'],fn(array $item):bool=>$item['id']===$userId)) ?: null;
        if (!$user) rr_json(['ok'=>false,'error'=>'A felhasználó nem található.'],404);
        $devices = array_values(array_filter($db['devices'],fn(array $device):bool=>$device['userId']===$userId));
        $snapshots=[]; foreach($devices as $device) if(isset($db['snapshots'][$device['id']])) $snapshots[]=$db['snapshots'][$device['id']];
        usort($snapshots,fn(array $a,array $b):int=>($b['updatedAt']??0)<=>($a['updatedAt']??0));
        $places=$snapshots[0]['data']['places']??[];
        $selected=array_values(array_filter($places,fn(array $place):bool=>in_array((string)($place['id']??''),$placeIds,true)));
        if (!$selected) rr_json(['ok'=>false,'error'=>'A kijelölt megállók nem találhatók a legutóbbi mentésben.'],404);
        $central=array_values($db['centralStops']??[]);$index=[];
        foreach($central as $position=>$place){$key=strtolower(trim((string)($place['name']??''))).'|'.strtolower(trim((string)($place['officialAddress']??'')));$index[$key]=$position;}
        $added=0;$updated=0;
        foreach($selected as $place){if(($place['type']??'CLIENT')==='FUEL')$place['type']='CLIENT';$key=strtolower(trim((string)($place['name']??''))).'|'.strtolower(trim((string)($place['officialAddress']??'')));if(isset($index[$key])){$central[$index[$key]]=$place;$updated++;}else{$index[$key]=count($central);$central[]=$place;$added++;}}
        $db['centralStops']=$central;$db['centralStopsUpdatedAt']=time();
        $db['audit'][]=['at'=>time(),'adminUserId'=>$session['userId'],'action'=>'central-stops-import','targetType'=>'user','targetId'=>$userId,'value'=>['selected'=>count($selected),'added'=>$added,'updated'=>$updated]];
        $db['audit']=array_slice($db['audit'],-1000);
        return ['imported'=>count($selected),'added'=>$added,'updated'=>$updated,'total'=>count($central),'updatedAt'=>$db['centralStopsUpdatedAt']];
    });
    rr_json(['ok'=>true]+$result);
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

if ($action === 'user-settings' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    $input=rr_input(); $userId=trim((string)($input['userId']??''));
    $name=trim((string)($input['displayName']??'')); $status=(string)($input['accessStatus']??'ACTIVE');
    $role=(string)($input['role']??'user'); $policy=(string)($input['syncPolicy']??'EVERY_STOP');
    if(!in_array($status,['ACTIVE','PENDING','BLOCKED'],true)||!in_array($role,['user','admin'],true)||!in_array($policy,['APP_ACTIVITY','WORK_END','EVERY_STOP'],true))rr_json(['ok'=>false,'error'=>'Érvénytelen beállítás.'],422);
    rr_transaction(function(array &$db)use($userId,$name,$status,$role,$policy,$session):void{
        $found=false;foreach($db['users'] as &$user)if($user['id']===$userId){$user['displayName']=$name;$user['accessStatus']=$status;$user['enabled']=$status!=='BLOCKED';$user['role']=$role;$user['syncPolicy']=$policy;$found=true;break;}
        if(!$found)rr_json(['ok'=>false,'error'=>'A felhasználó nem található.'],404);
        $db['audit'][]=['at'=>time(),'adminUserId'=>$session['userId'],'action'=>'user-settings','targetType'=>'user','targetId'=>$userId,'value'=>['name'=>$name,'status'=>$status,'role'=>$role,'policy'=>$policy]];
    });rr_json(['ok'=>true]);
}

if ($action === 'sync-policy' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    $input=rr_input(); $userId=trim((string)($input['userId']??'')); $policy=(string)($input['policy']??'EVERY_STOP');
    if (!in_array($policy,['APP_ACTIVITY','WORK_END','EVERY_STOP'],true)) rr_json(['ok'=>false,'error'=>'Érvénytelen szinkronizálási beállítás.'],422);
    rr_transaction(function(array &$db) use($userId,$policy,$session):void { $found=false; foreach($db['users'] as &$user) if($user['id']===$userId){$user['syncPolicy']=$policy;$found=true;break;} if(!$found)rr_json(['ok'=>false,'error'=>'A felhasználó nem található.'],404); $db['audit'][]=['at'=>time(),'adminUserId'=>$session['userId'],'action'=>'sync-policy','targetType'=>'user','targetId'=>$userId,'value'=>$policy]; });
    rr_json(['ok'=>true]);
}

rr_json(['ok' => false, 'error' => 'Ismeretlen művelet.'], 404);
