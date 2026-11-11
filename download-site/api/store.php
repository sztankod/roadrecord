<?php
declare(strict_types=1);

function rr_empty_store(): array {
    return ['users' => [], 'devices' => [], 'otps' => [], 'sessions' => [], 'snapshots' => [], 'centralStops' => [], 'centralStopsUpdatedAt' => null, 'audit' => []];
}

function rr_store_path(): string {
    $dir = __DIR__ . '/storage';
    if (!is_dir($dir) && !mkdir($dir, 0700, true) && !is_dir($dir)) throw new RuntimeException('A tárhely nem hozható létre.');
    return $dir . '/roadrecord-data.php';
}

function rr_key(): string {
    $key = hex2bin(RR_DATA_KEY);
    if ($key === false || strlen($key) !== 32) throw new RuntimeException('Hibás szerverkulcs.');
    return $key;
}

function rr_load_store(): array {
    $path = rr_store_path();
    if (!is_file($path)) return rr_empty_store();
    $raw = file_get_contents($path);
    $encoded = substr((string)$raw, strpos((string)$raw, "\n") + 1);
    $blob = base64_decode(trim($encoded), true);
    if ($blob === false || strlen($blob) < 29) throw new RuntimeException('Sérült adattár.');
    $nonce = substr($blob, 0, 12);
    $tag = substr($blob, 12, 16);
    $plain = openssl_decrypt(substr($blob, 28), 'aes-256-gcm', rr_key(), OPENSSL_RAW_DATA, $nonce, $tag);
    if ($plain === false) throw new RuntimeException('Az adattár nem oldható fel.');
    $data = json_decode($plain, true);
    return is_array($data) ? array_replace(rr_empty_store(), $data) : rr_empty_store();
}

function rr_save_store(array $data): void {
    $path = rr_store_path();
    $nonce = random_bytes(12);
    $tag = '';
    $cipher = openssl_encrypt(json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES), 'aes-256-gcm', rr_key(), OPENSSL_RAW_DATA, $nonce, $tag);
    if ($cipher === false) throw new RuntimeException('Az adattár nem titkosítható.');
    $temp = $path . '.tmp';
    file_put_contents($temp, "<?php exit; ?>\n" . base64_encode($nonce . $tag . $cipher), LOCK_EX);
    chmod($temp, 0600);
    if (!rename($temp, $path)) throw new RuntimeException('Az adattár nem menthető.');
}

function rr_transaction(callable $operation): mixed {
    $lockPath = dirname(rr_store_path()) . '/store.lock';
    $lock = fopen($lockPath, 'c+');
    if (!$lock || !flock($lock, LOCK_EX)) throw new RuntimeException('Az adattár foglalt.');
    try {
        $data = rr_load_store();
        $result = $operation($data);
        rr_save_store($data);
        return $result;
    } finally {
        flock($lock, LOCK_UN);
        fclose($lock);
    }
}
