package com.sdk.esc;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import print.Print;
import print.PublicFunction;

public class PublicAction {
    private final Context context;


    public PublicAction(Context con) {
        context = con;
    }


    public void BeforePrintActionText() {
        try {
            PublicFunction PFun = new PublicFunction(context);
            if (!TextUtils.isEmpty(PFun.ReadSharedPreferencesData("Codepage"))) {
                String codepage = PFun.ReadSharedPreferencesData("Codepage").split(",")[1];
                //设置Codepage
                Print.LanguageEncode = PublicFunction.getLanguageEncode(codepage);

            }
        } catch (Exception e) {
            Log.e("Print", "PublicAction --> BeforePrintAction " + e.getMessage());
        }
    }

    public void AfterPrintActionText() {

    }

}