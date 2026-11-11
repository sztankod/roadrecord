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
        rr_smtp_command($socket, 'RCPT TO:<' . $recipient . '>', [250, 251]);
        rr_smtp_command($socket, 'DATA', [354]);
        $subject = "RoadRecord ellenőrzőkód: {$code}";
        $body = "RoadRecord belépési kód: {$code}\r\n\r\nA kód 10 percig érvényes. Ha nem te kérted, hagyd figyelmen kívül ezt a levelet.";
        $message = "From: RoadRecord <" . RR_SMTP_USER . ">\r\nTo: <{$recipient}>\r\nSubject: =?UTF-8?B?" . base64_encode($subject) . "?=\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: 8bit\r\n\r\n{$body}\r\n.";
        rr_smtp_command($socket, $message, [250]);
        rr_smtp_command($socket, 'QUIT', [221]);
    } finally {
        fclose($socket);
    }
}
