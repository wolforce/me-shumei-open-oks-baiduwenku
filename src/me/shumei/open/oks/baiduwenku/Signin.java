package me.shumei.open.oks.baiduwenku;

import java.io.IOException;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;

import android.content.Context;

/**
 * 使签到类继承CommonData，以方便使用一些公共配置信息
 * @author wolforce
 *
 */
public class Signin extends CommonData {
	String resultFlag = "false";
	String resultStr = "未知错误！";
	
	String wenkuUrl = "http://wapwenku.baidu.com/uc/";
	String loginUrl = "http://wenku.baidu.com/login";
	
	/**
	 * <p><b>程序的签到入口</b></p>
	 * <p>在签到时，此函数会被《一键签到》调用，调用结束后本函数须返回长度为2的一维String数组。程序根据此数组来判断签到是否成功</p>
	 * @param ctx 主程序执行签到的Service的Context，可以用此Context来发送广播
	 * @param isAutoSign 当前程序是否处于定时自动签到状态<br />true代表处于定时自动签到，false代表手动打开软件签到<br />一般在定时自动签到状态时，遇到验证码需要自动跳过
	 * @param cfg “配置”栏内输入的数据
	 * @param user 用户名
	 * @param pwd 解密后的明文密码
	 * @return 长度为2的一维String数组<br />String[0]的取值范围限定为两个："true"和"false"，前者表示签到成功，后者表示签到失败<br />String[1]表示返回的成功或出错信息
	 */
	public String[] start(Context ctx, boolean isAutoSign, String cfg, String user, String pwd) {
		//把主程序的Context传送给验证码操作类，此语句在显示验证码前必须至少调用一次
		CaptchaUtil.context = ctx;
		//标识当前的程序是否处于自动签到状态，只有执行此操作才能在定时自动签到时跳过验证码
		CaptchaUtil.isAutoSign = isAutoSign;
		
		try{
			//存放Cookies的HashMap
			HashMap<String, String> cookies = new HashMap<String, String>();
			//Jsoup的Response
			Response res;
			
			//获取自定义配置View里设定的百度登录方式的值，默认使用Android登录
			int baidulogintype = 1;
			try {
				JSONObject jsonObj = new JSONObject(cfg);
				baidulogintype = jsonObj.getInt("logintype");
			} catch (Exception e) {
				e.printStackTrace();
			}
			//根据设定的方式登录，获取Cookies
			switch (baidulogintype) {
				case 0:
					cookies = BaiduLoginMethod.loginBaiduWeb(user, pwd);
					break;
				case 1:
					cookies = BaiduLoginMethod.loginBaiduAndroid(user, pwd);
					break;
				case 2:
					cookies = BaiduLoginMethod.loginBaiduWap(user, pwd);
					break;
			}
			//判断是否登录成功
			String customLoginErrorType = cookies.get(BaiduLoginMethod.CUSTOM_COOKIES_KEY);
			if (customLoginErrorType.equals(BaiduLoginMethod.ERROR_LOGIN_SUCCEED)) {
				//登录成功，把标记用的Cookies删除掉
				cookies.remove(BaiduLoginMethod.CUSTOM_COOKIES_KEY);
			} else {
				//登录失败，直接跳出签到函数
				if (customLoginErrorType.equals(BaiduLoginMethod.ERROR_ACCOUNT_INFO)) {
					resultFlag = "false";
					resultStr = "登录失败，有可能是账号或密码错误";
				} else if (customLoginErrorType.equals(BaiduLoginMethod.ERROR_CANCEL_CAPTCHA)) {
					resultFlag = "false";
					resultStr = "用户取消输入验证码";
				} else if (customLoginErrorType.equals(BaiduLoginMethod.ERROR_DOWN_CAPTCHA)) {
					resultFlag = "false";
					resultStr = "拉取验证码错误";
				} else if (customLoginErrorType.equals(BaiduLoginMethod.ERROR_INPUT_CAPTCHA)) {
					resultFlag = "false";
					resultStr = "输入的验证码错误";
				}
				return new String[]{resultFlag, resultStr};
			}
			
			//登录文库
			res = Jsoup.connect(wenkuUrl).cookies(cookies).userAgent(UA_BAIDU_PC).referrer(wenkuUrl).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
			cookies.putAll(res.cookies());
			//提交登录通知
			Jsoup.connect(loginUrl).cookies(cookies).userAgent(UA_BAIDU_PC).referrer(wenkuUrl).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
			resultFlag = "true";
			try {
				StringBuilder sb = new StringBuilder();
				sb.append("登录成功");
				//分纯WAP页面与3G页面解析，如果纯WAP页面不能获取到数据，那就再访问3G页面
				try {
					//纯WAP页面
					sb.append("\n" + res.parse().select("div.chui").text());
				} catch (Exception e) {
					try {
						String returnStr = res.parse().select("#page script").html();
						//用正则查找账号信息
						Pattern pattern = Pattern.compile("userExp:.+'(\\d+)'.+userWea:.+'(\\d+)'");
						Matcher matcher = pattern.matcher(returnStr);
						if(matcher.find())
						{
							//0=>userExp: '80',userWea: '52'
							//1=>80
							//2=>52
							sb.append("\n经验值" + matcher.group(1));
							sb.append("\n财富值" + matcher.group(2));
						}
					} catch (Exception e2) {
						sb.append("登录成功");
					}
				}
				resultStr = sb.toString();

			} catch (Exception e) {
				resultStr = "登录成功";
			}
			
		} catch (IOException e) {
			this.resultFlag = "false";
			this.resultStr = "连接超时";
			e.printStackTrace();
		} catch (Exception e) {
			this.resultFlag = "false";
			this.resultStr = "未知错误！";
			e.printStackTrace();
		}
		
		return new String[]{resultFlag, resultStr};
	}
	
	
}
