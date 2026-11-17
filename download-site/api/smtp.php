<?php
declare(strict_types=1);

function rr_smtp_read($socket): string {
    $result = '';
    while (($line = fgets($socket, 1024)) !== false) {
        $result .= $line;
        if (strlen($line) < 4 || $line[3] === ' ') break;
    }
    return $result;
}

function rr_smtp_command($socket, string $command, array $codes): string {
    if ($command !== '') fwrite($socket, $command . "\r\n");
    $reply = rr_smtp_read($socket);
    $code = (int)substr($reply, 0, 3);
    if (!in_array($code, $codes, true)) throw new RuntimeException('Levélküldési hiba: ' . $code);
    return $reply;
}

function rr_send_code(string $recipient, string $code): void {
    rr_send_mail([$recipient], "RoadRecord ellenőrzőkód: {$code}", nl2br(htmlspecialchars("RoadRecord belépési kód: {$code}\n\nA kód 10 percig érvényes. Ha nem te kérted, hagyd figyelmen kívül ezt a levelet.", ENT_QUOTES, 'UTF-8')));
}

function rr_send_mail(array $recipients, string $subject, string $html): void {
    $recipients = array_values(array_unique(array_filter(array_map('trim', $recipients), fn(string $email): bool => filter_var($email, FILTER_VALIDATE_EMAIL) !== false)));
    if (!$recipients) throw new RuntimeException('Nincs érvényes címzett.');
    $socket = stream_socket_client('tcp://smtp.gmail.com:587', $error, $message, 20, STREAM_CLIENT_CONNECT);
    if (!$socket) throw new RuntimeException('A levelezőszerver nem érhető el.');
    stream_set_timeout($socket, 20);
    try {
        rr_smtp_command($socket, '', [220]);
        rr_smtp_command($socket, 'EHLO roadrecord.widor.nhely.hu', [250]);
        rr_smtp_command($socket, 'STARTTLS', [220]);
        if (!stream_socket_enable_crypto($socket, true, STREAM_CRYPTO_METHOD_TLS_CLIENT)) throw new RuntimeException('A levelezési titkosítás nem indítható.');
        rr_smtp_command($socket, 'EHLO roadrecord.widor.nhely.hu', [250]);
        rr_smtp_command($socket, 'AUTH LOGIN', [334]);
        rr_smtp_command($socket, base64_encode(RR_SMTP_USER), [334]);
        rr_smtp_command($socket, base64_encode(RR_SMTP_PASSWORD), [235]);
        rr_smtp_command($socket, 'MAIL FROM:<' . RR_SMTP_USER . '>', [250]);
        foreach ($recipients as $recipient) rr_smtp_command($socket, 'RCPT TO:<' . $recipient . '>', [250, 251]);
        rr_smtp_command($socket, 'DATA', [354]);
        $headers = "From: RoadRecord <" . RR_SMTP_USER . ">\r\nTo: " . implode(', ', array_map(fn(string $email): string => '<' . $email . '>', $recipients)) . "\r\nSubject: =?UTF-8?B?" . base64_encode($subject) . "?=\r\nMIME-Version: 1.0\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Transfer-Encoding: 8bit";
        $payload = $headers . "\r\n\r\n" . preg_replace('/(?m)^\./', '..', $html) . "\r\n.";
        rr_smtp_command($socket, $payload, [250]);
        rr_smtp_command($socket, 'QUIT', [221]);
    } finally {
        fclose($socket);
    }
}
