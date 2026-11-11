<?php
declare(strict_types=1);
require_once __DIR__ . '/bootstrap.php';
$session=rr_session();
$result=rr_transaction(function(array &$db) use($session):array {
    $snapshots=[]; foreach($db['snapshots'] as $snapshot) if(($snapshot['userId']??'')===$session['userId'])$snapshots[]=$snapshot;
    usort($snapshots,fn(array $a,array $b):int=>($b['updatedAt']??0)<=>($a['updatedAt']??0));
    return ['stops'=>$snapshots[0]['data']['places']??[],'updatedAt'=>$snapshots[0]['updatedAt']??null];
});
rr_json(['ok'=>true]+$result);
