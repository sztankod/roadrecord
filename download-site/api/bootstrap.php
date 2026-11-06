<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/store.php';

function rr_json(array $value, int $status = 200): never {
    http_response_code($status);
    echo json_encode($value, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function rr_input(): array {
    $raw = file_get_contents('php://input');
    $value = json_decode($raw ?: '{}', true);
    if (!is_array($value)) rr_json(['ok' => false, 'error' => 'Érvénytelen kérés.'], 400);
    return $value;
}

function rr_email(mixed $value): string {
    $email = strtolower(trim((string)$value));
    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) rr_json(['ok' => false, 'error' => 'Érvénytelen e-mail-cím.'], 422);
    return $email;
}

function rr_bearer(): string {
    $header = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if (!preg_match('/^Bearer\s+(.+)$/i', $header, $match)) rr_json(['ok' => false, 'error' => 'Bejelentkezés szükséges.'], 401);
    return trim($match[1]);
}

function rr_session(bool $admin = false): array {
    $hash = hash('sha256', rr_bearer());
    $now = time();
    $found = null;
    rr_transaction(function (array &$db) use ($hash, $now, &$found): void {
        foreach ($db['sessions'] as &$session) {
            if (hash_equals($session['tokenHash'], $hash) && $session['expiresAt'] > $now) {
                $session['lastSeenAt'] = $now;
                $found = $session;
                break;
            }
        }
    });
    if (!$found) rr_json(['ok' => false, 'error' => 'A munkamenet lejárt.'], 401);
    if ($admin && ($found['role'] ?? '') !== 'admin') rr_json(['ok' => false, 'error' => 'Nincs adminisztrátori jogosultság.'], 403);
    return $found;
}

function rr_id(string $prefix): string {
    return $prefix . '_' . bin2hex(random_bytes(12));
}
