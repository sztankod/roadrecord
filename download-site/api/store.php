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
    if ($raw === false) throw new RuntimeException('Az adattár nem olvasható.');
    // A zero-byte file can be left behind by an interrupted first write. There is
    // no payload to recover in that case, so bootstrap a fresh encrypted store.
    if ($raw === '') return rr_empty_store();
    $separator = strpos($raw, "\n");
    if ($separator === false) {
        if (trim($raw) === '<?php exit; ?>') throw new RuntimeException('Az adattár mentése félbeszakadt.');
        if (is_array(json_decode($raw, true))) throw new RuntimeException('Régi adattárformátum.');
        throw new RuntimeException('Az adattár fejléce sérült.');
    }
    $encoded = substr($raw, $separator + 1);
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
    $payload = "<?php exit; ?>\n" . base64_encode($nonce . $tag . $cipher);
    $written = file_put_contents($temp, $payload, LOCK_EX);
    if ($written === false || $written !== strlen($payload)) {
        @unlink($temp);
        throw new RuntimeException('Az adattár nem menthető.');
    }
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
