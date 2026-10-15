package com.m000gg.plugins.emailnotifier;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

public class EmailSender {

    private final String from;
    private final Properties props = new Properties();
    private final Authenticator authenticator;

    public EmailSender(SMTPServerConfiguration smtpServerConfiguration) {
        this.from = smtpServerConfiguration.from();

        props.put("mail.smtp.host", smtpServerConfiguration.host());
        props.put("mail.smtp.port", String.valueOf(smtpServerConfiguration.portValue()));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");

        if (smtpServerConfiguration.username() != null) {
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            this.authenticator = new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpServerConfiguration.username(), smtpServerConfiguration.password());
                }
            };
        } else {
            this.authenticator = null;
        }
    }

    public void send(BalanceNotificationEmailMessage emailMessage) throws MessagingException {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(EmailSender.class.getClassLoader());
        try {
            Session session = Session.getInstance(props, authenticator);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(emailMessage.to()));
            message.setSubject(emailMessage.subject(), "UTF-8");
            message.setText(emailMessage.body(), "UTF-8");
            Transport.send(message);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }
}
