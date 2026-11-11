<?php
declare(strict_types=1);
require_once __DIR__ . '/bootstrap.php';
$session=rr_session();
if ($_SERVER['REQUEST_METHOD']==='GET') {
    $result=rr_transaction(fn(array &$db):array=>['stops'=>array_values($db['centralStops']??[]),'updatedAt'=>$db['centralStopsUpdatedAt']??null]);
    rr_json(['ok'=>true]+$result);
}
if ($_SERVER['REQUEST_METHOD']==='POST') {
    $input=rr_input(); $stops=$input['stops']??null;
    if (!is_array($stops)) rr_json(['ok'=>false,'error'=>'Hiányzó megállólista.'],422);
    if (count($stops)>1000) rr_json(['ok'=>false,'error'=>'Túl sok megálló.'],413);
    rr_transaction(function(array &$db) use($session,$stops):void {
        $user=current(array_filter($db['users'],fn(array $u):bool=>$u['id']===$session['userId']))?:null;
        if (!$user || ($user['role']??'user')!=='admin') rr_json(['ok'=>false,'error'=>'Csak admin tölthet fel központi megállókat.'],403);
        $db['centralStops']=array_values($stops); $db['centralStopsUpdatedAt']=time();
        $db['audit'][]=['at'=>time(),'adminUserId'=>$session['userId'],'action'=>'central-stops-update','targetType'=>'central','targetId'=>'stops','value'=>count($stops)];
    });
    rr_json(['ok'=>true,'count'=>count($stops)]);
}
rr_json(['ok'=>false],405);
