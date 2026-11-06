<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

echo json_encode([
    'ok' => true,
    'php' => PHP_VERSION,
    'sqlite' => extension_loaded('pdo_sqlite'),
    'openssl' => extension_loaded('openssl'),
    'json' => extension_loaded('json'),
], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
