package com.example.sfn.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.example.sfn.lambda.dto.ApprovalResult;
import jakarta.inject.Named;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.SendTaskFailureRequest;
import software.amazon.awssdk.services.sfn.model.SendTaskSuccessRequest;

import java.util.Map;

@Named("approvalHttpHandler")
public class ApprovalHttpHandler implements RequestHandler<Map<String, Object>, ApprovalResult> {

    @Override
    public ApprovalResult handleRequest(Map<String, Object> event, Context context) {
        // Extrair parâmetros da URL (Lambda URL)
        Map<String, String> queryParams = (Map<String, String>) event.get("queryStringParameters");
        String taskToken = queryParams.get("taskToken");
        String pedidoId = queryParams.get("pedidoId");

        String action = "123".equals(pedidoId) ? "approve" : "reject";

        return processApproval(taskToken, action, context.getLogger());
    }

    private ApprovalResult processApproval(String taskToken, String action, LambdaLogger logger) {
        ApprovalResult result = new ApprovalResult();

        try (var sfnClient = SfnClient.builder().region(Region.SA_EAST_1).build()) {
            if ("approve".equals(action)) {
                var request = SendTaskSuccessRequest.builder()
                        .taskToken(taskToken)
                        .output("{\"status\":\"OK\"}")
                        .build();

                sfnClient.sendTaskSuccess(request);

                result.setStatus("SUCCESS");
                result.setMessage("Pedido aprovado com sucesso!");

                logger.log("SendTaskSuccess chamado com sucesso!");
            } else {
                var request = SendTaskFailureRequest.builder()
                        .taskToken(taskToken)
                        .error("ErroExemplo")
                        .cause("Algum motivo de falha")
                        .build();
                sfnClient.sendTaskFailure(request);

                result.setStatus("SUCCESS");
                result.setMessage("Pedido rejeitado com sucesso!");

                logger.log("SendTaskFailure chamado!");
            }
        } catch (Exception e) {
            logger.log("Erro ao enviar o callback para a Step Functions: " + e.getMessage());
            result.setStatus("ERROR");
            result.setMessage("Erro ao processar a aprovação: " + e.getMessage());
        }
        return result;

    }
}