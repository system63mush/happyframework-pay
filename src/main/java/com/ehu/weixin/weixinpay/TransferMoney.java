package com.ehu.weixin.weixinpay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.ehu.bean.LowerUnderscoreFilter;
import com.ehu.bean.PayResponse;
import com.ehu.config.EhPayConfig;
import com.ehu.exception.PayException;
import com.ehu.weixin.entity.TransferToBankCardParams;
import com.ehu.weixin.entity.WechatBusinessPay;
import com.ehu.weixin.util.Signature;
import com.ehu.weixin.util.WeChatUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;


/**
 * @author AlanSun
 * @Date 2016年8月3日
 * 微信企业付款操作类
 */
public class TransferMoney {

    private static final String REQUESTURL = "https://api.mch.weixin.qq.com/mmpaymkttransfers/promotion/transfers";

    /**
     * 转账到银行卡
     */
    private static final String URL_TOBANK = "https://api.mch.weixin.qq.com/mmpaysptrans/pay_bank";

    @SuppressWarnings("unchecked")
    public static boolean weChatPayBusinessPayforUser(WechatBusinessPay wechatBusinessPay) throws PayException {
        EhPayConfig config = EhPayConfig.getInstance();

        SortedMap<String, String> packageParams = new TreeMap<String, String>();
        packageParams.put("mch_appid", config.getWxPay_mch_appid());
        packageParams.put("mchid", config.getWxPay_mch_id());
        packageParams.put("nonce_str", WeChatUtils.getNonceStr());
        packageParams.put("partner_trade_no", wechatBusinessPay.getOrderId());
        packageParams.put("openid", wechatBusinessPay.getOpenId());
        /*NO_CHECK：不校验真实姓名
        FORCE_CHECK：强校验真实姓名（未实名认证的用户会校验失败，无法转账）*/
        packageParams.put("check_name", wechatBusinessPay.getCheckName());
        if (!wechatBusinessPay.getCheckName().equals("NO_CHECK")) {
            packageParams.put("re_user_name", wechatBusinessPay.getReUserName());
        }
        packageParams.put("amount", WeChatUtils.getFinalMoney(wechatBusinessPay.getAmount()));
        packageParams.put("spbill_create_ip", config.getWxPay_spbill_create_ip());
        packageParams.put("desc", wechatBusinessPay.getDesc());
        packageParams.put("sign", Signature.getSign(packageParams));
        Map<String, String> map = WeChatUtils.wechatPostWithSSL(packageParams, REQUESTURL, config.getWxPay_ca(), config.getWxPay_code());//发送得到微信服务器
        return WeChatUtils.checkWechatResponse(map);
    }

    /**
     * 转账到银行卡
     *
     * @param params {@link TransferToBankCardParams}
     * @return {@link PayResponse}
     */
    public static PayResponse<Boolean> transferToBankCard(TransferToBankCardParams params) throws PayException {
        EhPayConfig config = EhPayConfig.getInstance();
        params.setAmount(Integer.parseInt(WeChatUtils.getFinalMoney(params.getAmount())));
        String encBankNo = params.getEncBankNo();
//        RSAPublicKeyImpl rsaPublicKey = new RSAPublicKeyImpl();
        String s = JSON.toJSONString(params, new LowerUnderscoreFilter());
        HashMap<String, String> packageParams = JSON.parseObject(s, new TypeReference<HashMap<String, String>>() {

        });
        packageParams.put("mch_id", config.getWxPay_mch_id());
        packageParams.put("nonce_str", WeChatUtils.getNonceStr());
        packageParams.put("sign", Signature.getSign(packageParams));
        return null;
    }
}