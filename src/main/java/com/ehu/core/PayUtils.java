package com.ehu.core;

import com.ehu.alipay.AlipayUtils;
import com.ehu.bean.PayInfoResponse;
import com.ehu.bean.OrderPay;
import com.ehu.bean.PayType;

/**
 * @author AlanSun
 * @date 2019/7/4 16:06
 **/
public class PayUtils {

    public static PayInfoResponse getPrePayInfo(OrderPay orderPay) {
        if (PayType.PAY_TYPE_1 == orderPay.getPayType()) {
            AlipayUtils.
        } else {

        }
        return null;
    }
}
