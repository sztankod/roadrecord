<?php
declare(strict_types=1);
require_once __DIR__ . '/bootstrap.php';

if (($_GET['check'] ?? '') === 'store') {
    try {
        rr_load_store();
        rr_json(['ok' => true, 'storeReadable' => true]);
    } catch (Throwable $error) {
        $known = [
            'A tárhely nem hozható létre.' => 'STORAGE_DIRECTORY',
            'Hibás szerverkulcs.' => 'KEY_FORMAT',
            'Az adattár nem olvasható.' => 'READ_FAILED',
            'Az adattár fejléce sérült.' => 'HEADER_MISSING',
            'Sérült adattár.' => 'CORRUPT_BLOB',
            'Az adattár nem oldható fel.' => 'DECRYPT_FAILED',
            'Az adattár foglalt.' => 'LOCK_FAILED',
        ];
        $code = $known[$error->getMessage()] ?? ($error instanceof TypeError ? 'TYPE_ERROR' : 'UNKNOWN_STORE_ERROR');
        rr_json(['ok' => false, 'storeReadable' => false, 'errorCode' => $code], 500);
    }
}

rr_json(['ok' => true]);
