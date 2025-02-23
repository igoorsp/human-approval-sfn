package com.example.sfn.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.example.sfn.lambda.dto.SqsMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Named("sqsEmailHandler")
@ApplicationScoped
public class SqsEmailHandler implements RequestHandler<SQSEvent, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqsEmailHandler.class);

    private final SesClient sesClient;
    private final DynamoDbClient dynamoClient;

    @Inject
    public SqsEmailHandler(final SesClient sesClient,
                           final DynamoDbClient dynamoClient ) {
        this.sesClient = sesClient;
        this.dynamoClient = dynamoClient;
    }

    @ConfigProperty(name = "app.base-url")
    String baseUrl;

    @ConfigProperty(name = "app.email")
    String email;

    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage msg : event.getRecords()) {
            SqsMessage sqsMessage = parseMessage(msg.getBody());

            // Salvar no DynamoDB para validação posterior
            //saveToDynamo(sqsMessage.getTaskToken(), sqsMessage.getPedidoId());

            // Enviar email com links de aprovação
            sendEmail(sqsMessage);
        }
        return null;
    }

    private void sendEmail(SqsMessage sqsMessage) {
        try {
            // 1. Validação básica do email
            if (sqsMessage.getUsuarioEmail() == null || sqsMessage.getUsuarioEmail().isBlank()) {
                LOGGER.error("Email do destinatário inválido para o pedido {}", sqsMessage.getPedidoId());
                return;
            }

            String encodedToken = URLEncoder.encode(
                    sqsMessage.getTaskToken(),
                    StandardCharsets.UTF_8
            );

            // 2. Construir URLs manualmente
            String approveUrl = String.format(
                    "%s/approve?taskToken=%s",
                    baseUrl,
                    encodedToken
            );

            String rejectUrl = String.format(
                    "%s/reject?taskToken=%s",
                    baseUrl,
                    encodedToken
            );

            // 3. Template HTML profissional com fallback
            String htmlBody = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <style>
                            .container { max-width: 600px; margin: 20px auto; font-family: Arial, sans-serif; }
                            .button { 
                                display: inline-block; padding: 12px 24px; 
                                margin: 10px; border-radius: 4px; text-decoration: none; 
                                color: white; font-weight: bold; 
                            }
                            .approve { background-color: #28a745; }
                            .reject { background-color: #dc3545; }
                            .footer { margin-top: 30px; color: #666; font-size: 0.9em; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <h2>Aprovação do Pedido #%s</h2>
                            <p>Por favor, revise e tome uma decisão:</p>
                            <div>
                                <a href="%s" class="button approve">Aprovar Pedido</a>
                                <a href="%s" class="button reject">Rejeitar Pedido</a>
                            </div>
                            <div class="footer">
                                <p>Este é um email automático. Não responda a esta mensagem.</p>
                                <p>Links válidos por 7 dias.</p>
                            </div>
                        </div>
                    </body>
                    </html>
                    """.formatted(
                    sqsMessage.getPedidoId(),
                    approveUrl,
                    rejectUrl
            );

            // 4. Configuração completa do email
            SendEmailRequest emailRequest = SendEmailRequest.builder()
                    .source(email)
                    .destination(d -> d.toAddresses(sqsMessage.getUsuarioEmail()))
                    .message(m -> m
                            .subject(s -> s.data("[Sistema de Aprovação] Pedido #" + sqsMessage.getPedidoId())) //
                            .body(b -> b
                                    .html(h -> h.data(htmlBody))
                                    .text(t -> t.data("Para aprovar o pedido, acesse: " + approveUrl))
                            )
                    )

                    .build();

            // 5. Envio com tratamento de erros
            sesClient.sendEmail(emailRequest);
            LOGGER.info("Email enviado com sucesso para {}", sqsMessage.getUsuarioEmail());

        } catch (SesException e) {
            LOGGER.error("Falha ao enviar email para {}: {}", sqsMessage.getUsuarioEmail(), e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            LOGGER.error("Erro inesperado ao enviar email: {}", e.getMessage());
        }
    }

    private SqsMessage parseMessage(String messageBody) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            SqsMessage message = objectMapper.readValue(messageBody, SqsMessage.class);

            if (message.getTaskToken() == null || message.getPedidoId() == null) {
                throw new RuntimeException("Campos obrigatórios faltando");
            }

            return message;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON inválido: " + messageBody, e);
        }
    }
}