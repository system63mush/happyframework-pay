package com.ehu.alipay;

import com.alan344happyframework.util.StringUtils;
import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayResponse;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.*;
import com.alipay.api.request.*;
import com.alipay.api.response.AlipayDataDataserviceBillDownloadurlQueryResponse;
import com.alipay.api.response.AlipayTradeAppPayResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.ehu.alipay.entity.TransferSingleParams;
import com.ehu.bean.*;
import com.ehu.config.AliPay;
import com.ehu.constants.PayBaseConstants;
import com.ehu.constants.PayResultCodeConstants;
import com.ehu.constants.PayResultMessageConstants;
import com.ehu.core.Pay;
import com.ehu.core.responsehandler.AliResponseHandler;
import com.ehu.exception.PayException;
import com.github.rholder.retry.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


/**
 * 类名：AlipayUtils
 * 功能：支付宝接口公用工具类
 * 详细：该类是请求、通知返回两个文件所调用的公用函数核心处理文件
 * 日期：2015-01-10
 *
 * @author AlanSun
 */
@Slf4j
public class AlipayUtils implements Pay {

    public static final AliPay config = AliPay.getInstance();
    private static AlipayClient alipayClient = new DefaultAlipayClient(config.getGatewayUrl(), config.getAppId(), config.getPrivateKey(), "json", config.getInputCharset(), config.getOpenPublicKey());

    /**
     * 创建支付宝订单支付信息（无线）
     * <p>
     * 2.0
     *
     * @param order 订单信息
     * @return 支付宝订单支付信息（无线）
     */
    @Override
    public PayInfoResponse createPayInfo(OrderPay order) {
        //实例化具体API对应的request类,类名称和接口名称对应,当前调用接口名称：alipay.trade.app.pay
        AlipayTradeAppPayRequest request = new AlipayTradeAppPayRequest();

        //SDK已经封装掉了公共参数，这里只需要传入业务参数。以下方法为sdk的model入参方式(model和biz_content同时存在的情况下取biz_content)。
        AlipayTradeAppPayModel model = order.getAlipayTradeAppPayModel();
        if (model == null) {
            model = new AlipayTradeAppPayModel();
            model.setTimeoutExpress("90m");
        }

        model.setOutTradeNo(order.getOrderId());
        model.setBody(order.getBody());
        model.setSubject(order.getSubject());
        model.setTotalAmount(order.getPrice());
        //固定值
        model.setProductCode("QUICK_MSECURITY_PAY");
        request.setBizModel(model);
        request.setNotifyUrl(StringUtils.getDefaultIfNull(order.getNotifyUrl(), config.getNotifyUrl()));
        try {
            //这里和普通的接口调用不同，使用的是sdkExecute
            AlipayTradeAppPayResponse response = alipayClient.sdkExecute(request);
            //就是orderString 可以直接给客户端请求，无需再做处理。
            log.info(response.getBody());
            return PayInfoResponse.builder().alipayStr(response.getBody()).build();
        } catch (AlipayApiException e) {
            log.error("构建支付信息失败：", e);
            throw new PayException("构建支付信息失败,请重试");
        }
    }

    /**
     * 线下扫码支付：扫码支付获取二维码
     *
     * @return 二维码地址
     * @throws PayException e
     */
    @Override
    public String getQrCode(OrderScanPay payOrder) throws PayException {
        //创建API对应的request类
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        request.setNotifyUrl(StringUtils.getDefaultIfNull(payOrder.getNotifyUrl(), config.getNotifyUrl()));

        AlipayTradePrecreateModel alipayTradePrecreateModel = payOrder.getAlipayTradePrecreateModel();
        if (alipayTradePrecreateModel == null) {
            alipayTradePrecreateModel = new AlipayTradePrecreateModel();
            alipayTradePrecreateModel.setTimeoutExpress("90m");
        }
        alipayTradePrecreateModel.setOutTradeNo(payOrder.getOrderId());
        alipayTradePrecreateModel.setBody(payOrder.getBody());
        alipayTradePrecreateModel.setSubject(payOrder.getSubject());
        alipayTradePrecreateModel.setTotalAmount(payOrder.getPrice());
        alipayTradePrecreateModel.setStoreId(payOrder.getStoreId());

        request.setBizModel(alipayTradePrecreateModel);

        AlipayTradePrecreateResponse response;
        try {
            response = alipayClient.execute(request);
            if (PayBaseConstants.ALIPAY_RETURN_CODE_10000.equals(response.getCode())) {
                return response.getQrCode();
            }
        } catch (Exception e) {
            log.error("支付宝扫码错误", e);
            throw new PayException(PayResultCodeConstants.ALIPAY_SCAN_ERROR_30001, PayResultMessageConstants.ALIPAY_SCAN_ERROR_30001);
        }
        return null;
    }

    /**
     * 支付宝退款
     *
     * @throws PayException e
     */
    @Override
    public PayResponse refund(OrderRefund params) throws PayException {
        //创建API对应的request类
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();

        AlipayTradeRefundModel model = params.getAlipayTradeRefundModel();
        if (model == null) {
            model = new AlipayTradeRefundModel();
        }
        model.setOutTradeNo(params.getOrderId());
        model.setRefundAmount(params.getRefundAmount());
        model.setOutRequestNo(params.getRefundId());
        model.setRefundReason(params.getRefundReason());
        model.setRefundCurrency(params.getRefundCurrency());
        //设置业务参数
        request.setBizModel(model);

        AlipayResponse alipayResponse = null;
        try {
            Callable<AlipayResponse> callable = () -> alipayClient.execute(request);
            Retryer<AlipayResponse> retryer = getAliPayRetryer();
            alipayResponse = retryer.call(callable);
        } catch (Exception e) {
            log.error("支付宝扫码退款失败", e);
        }

        PayResponse handler = AliResponseHandler.getInstance().handler(alipayResponse);
        return handler;
    }

    /**
     * （1）单日转出累计额度为100万元。
     * <p>
     * （2）转账给个人支付宝账户，单笔最高5万元；转账给企业支付宝账户，单笔最高10万元。
     *
     * @param params {@link TransferSingleParams}
     */
    @Override
    public PayResponse<AlipayResponse> transferMoneyInternal(TransferMoneyInternal params) {
        AlipayFundTransToaccountTransferRequest request = new AlipayFundTransToaccountTransferRequest();
        AlipayFundTransToaccountTransferModel model = params.getFundTransToaccountTransferModel();

        if (model == null) {
            model = new AlipayFundTransToaccountTransferModel();
            model.setPayeeType("ALIPAY_LOGONID");
        }

        model.setAmount(params.getAmount());
        model.setOutBizNo(params.getTransferId());
        model.setRemark(params.getDesc());
        model.setPayeeAccount(params.getPayeeAccount());
        model.setPayerRealName(params.getReUserName());

//        String paramStr = JSON.toJSONString(params, new LowerUnderscoreFilter());
        request.setBizModel(model);

        Callable<AlipayResponse> callable = () -> alipayClient.execute(request);
        Retryer<AlipayResponse> retryer = getAliPayRetryer();

        AlipayResponse alipayResponse = null;
        try {
            alipayResponse = retryer.call(callable);
        } catch (ExecutionException | RetryException e) {
            log.error("alipay transfer error", e);
        }

        return responseHandler(alipayResponse);
    }

    /**
     * 查询支付状态
     *
     * @param params params
     * @return 订单状态
     * @throws PayException e
     */
    @Override
    public PayResponse queryOrder(OrderQuery params) throws PayException {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        AlipayTradeQueryModel tradeQueryModel = new AlipayTradeQueryModel();
        tradeQueryModel.setOutTradeNo(params.getOrderId());
        request.setBizModel(tradeQueryModel);

        Callable<AlipayTradeQueryResponse> callable = () -> alipayClient.execute(request);
        Retryer<AlipayTradeQueryResponse> retryer = getAliPayRetryer();

        AlipayTradeQueryResponse alipayResponse = null;
        try {
            alipayResponse = retryer.call(callable);
        } catch (Exception e) {
            log.error("支付宝扫码查询失败", e);
        }

        PayResponse response = responseHandler(alipayResponse);

        if (!PayBaseConstants.RETURN_SUCCESS.equals(response.getResultCode()) && alipayResponse != null) {
            if ("ACQ.TRADE_NOT_EXIST".equals(alipayResponse.getSubCode())) {
                throw new PayException(PayResultCodeConstants.TRADE_NOT_EXIST_30005, PayResultMessageConstants.TRADE_NOT_EXIST_30005);
            }
        }
        return response;
    }

    /**
     * 日期 只支持 yyyy-MM-dd 与yyyy-MM
     * if aliSrcPath is null then just return downloadUrl
     * otherwise return downloadUrl and download the file
     *
     * @param time       time
     * @param aliSrcPath downloanUrl
     * @throws PayException e
     */
    public static String getFinancial(String time, String aliSrcPath) throws PayException {
        AlipayDataDataserviceBillDownloadurlQueryRequest request = new AlipayDataDataserviceBillDownloadurlQueryRequest();
        AlipayDataDataserviceBillDownloadurlQueryModel downloadurlQueryModel = new AlipayDataDataserviceBillDownloadurlQueryModel();
        downloadurlQueryModel.setBillDate(time);
        downloadurlQueryModel.setBillType("trade");

        request.setBizModel(downloadurlQueryModel);

        AlipayDataDataserviceBillDownloadurlQueryResponse response;
        try {
            response = alipayClient.execute(request);
            if (response.isSuccess()) {
                try {
                    if (!StringUtils.isEmpty(aliSrcPath)) {
                        FileUtils.copyURLToFile(new URL(response.getBillDownloadUrl()), new File(aliSrcPath), 10000, 10000);
                    }
                } catch (IOException e) {
                    log.error("获取财务账单url失败", e);
                    return response.getBillDownloadUrl();
                }
                return response.getBillDownloadUrl();
            } else {
                log.error("获取财务失败", response.getCode() + response.getMsg());
                return null;
            }
        } catch (AlipayApiException e) {
            log.error("获取财务账单url失败", e);
            throw new PayException(PayResultCodeConstants.GET_FINANCIAL_30013, PayResultMessageConstants.GET_FINANCIAL_30013);
        }
    }

    //------------------------------------------------------------------------------------------------------------------

    /**
     * 处理支付宝返回结果
     *
     * @param call 支付宝返回结果
     */
    private static <T extends AlipayResponse> PayResponse<T> responseHandler(T call) {
        PayResponse<T> response = new PayResponse<>();
        log.info("支付宝返回信息：{}", JSON.toJSONString(call));
        if (null == call) {
            response.setResultCode(PayBaseConstants.RETURN_FAIL);
            response.setResultMessage("response null error");
        } else if (call.isSuccess()) {
            if (!PayBaseConstants.ALIPAY_RETURN_CODE_10000.equals(call.getCode())) {
                response.setResultCode(call.getSubCode());
                response.setResultMessage(call.getSubMsg());
                response.setData(call);
            }
        } else {
            response.setResultCode(call.getSubCode());
            response.setResultMessage(call.getSubMsg());
        }

        return response;
    }

    /**
     * 获取支付宝重试
     */
    private static <T extends AlipayResponse> Retryer<T> getAliPayRetryer() {
        return RetryerBuilder.<T>newBuilder()
                .retryIfException()
                .retryIfResult(response ->
                        response == null
//                                || !response.isSuccess()
                                || "ACQ.SYSTEM_ERROR".equals(response.getSubCode())
//                                || "40004".equals(response.getCode())
                                || "20000".equals(response.getCode()))
                .withWaitStrategy(WaitStrategies.fixedWait(1, TimeUnit.SECONDS))
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();
    }
}
