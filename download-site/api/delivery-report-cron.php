<?php
declare(strict_types=1);
require_once __DIR__ . '/bootstrap.php';
require_once __DIR__ . '/delivery-reports.php';
rr_json(['ok'=>true,'sent'=>rr_process_delivery_reports()]);
