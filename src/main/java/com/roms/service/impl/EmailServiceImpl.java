package com.roms.service.impl;

import com.roms.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Triển khai dịch vụ EmailService:
 * Sử dụng JavaMailSender để gửi email qua giao thức SMTP (Gmail).
 * Tự động kiểm tra cấu hình:
 * - Nếu có cấu hình spring.mail.username và JavaMailSender: Gửi email thực tế qua Gmail.
 * - Nếu không có cấu hình (trong môi trường Unit Test / Dev cục bộ): Tự động fallback ghi log ra console an toàn,
 *   giúp luồng nghiệp vụ không bị gián đoạn hay phát sinh lỗi crash.
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final String fromEmail;

    public EmailServiceImpl(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String fromEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
    }

    /**
     * Gửi email chứa mã OTP đến người dùng.
     */
    @Override
    public void sendOtpEmail(String toEmail, String otp) {
        log.info("==> Sending OTP to {}: [ {} ] (Valid for 5 minutes)", toEmail, otp);
        if (mailSender != null && StringUtils.hasText(fromEmail)) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromEmail);
                message.setTo(toEmail);
                message.setSubject("ROMS - Mã OTP Đặt Lại Mật Khẩu");
                message.setText("Xin chào,\n\nMã OTP đặt lại mật khẩu của bạn là: " + otp 
                        + "\nMã này có hiệu lực trong vòng 5 phút (tối đa 5 lần thử)."
                        + "\nVì lý do an toàn, vui lòng không chia sẻ mã này cho bất kỳ ai.");
                mailSender.send(message);
                log.info("Successfully sent OTP email to {} via Gmail SMTP", toEmail);
            } catch (Exception e) {
                log.error("Failed to send OTP email to {} via Gmail SMTP: {}", toEmail, e.getMessage(), e);
            }
        }
    }
}
