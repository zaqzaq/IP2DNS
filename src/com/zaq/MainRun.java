package com.zaq;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import com.aliyun.api.AliyunClient;
import com.aliyun.api.AliyunConstants;
import com.aliyun.api.DefaultAliyunClient;
import com.aliyun.api.dns.dns20150109.request.UpdateDomainRecordRequest;
import com.aliyun.api.dns.dns20150109.response.UpdateDomainRecordResponse;
import com.taobao.api.ApiException;

public class MainRun {
	private static String config = "conf.properties";
	private static AliyunClient client;
	private static Properties props;

	private static String localIP = "";// 本地ip
	private static String outIP = "";// 外网Ip
	private static Executor executor;

	private static URL url ;

	
	public static void init() throws IOException {
		executor = Executors.newSingleThreadExecutor();
		props = new Properties();
		InputStream is = new BufferedInputStream(MainRun.class.getClassLoader().getResourceAsStream(config));
		props.load(is);

		/*
		 * 详见
		 * https://docs.aliyun.com/?spm=5176.776555948.1863381.554.7Lk02r#/pub
		 * /dns
		 */

		String serverUrl = "http://dns.aliyuncs.com/";// 小万网云哥DNS解析
		String accessKeyId = "******";
		String accessKeySecret = "*******";

		client = new DefaultAliyunClient(serverUrl, accessKeyId, accessKeySecret, AliyunConstants.FORMAT_JSON);
		url = new URL(props.getProperty("ipurl", "http://1111.ip138.com/ic.asp"));

	}

	public static void main(String[] args) throws IOException, InterruptedException {
		init();
		/*
		 * DescribeDomainRecordsRequest request3=new
		 * DescribeDomainRecordsRequest(); DescribeDomainRecordsResponse
		 * response3; request3.setDomainName("freshz.cn"); try { response3 =
		 * client.execute(request3);
		 * 
		 * System.out.println(response3.getBody()+response3.getMessage()); }
		 * catch (ApiException e) { e.printStackTrace(); }
		 */

		while (true) {
			{// 绑定本地Ip到local
				String ipLocal = getLocalIP();
				System.out.println(ipLocal);

				if (!localIP.equals(ipLocal)) {
					UpdateDomainRecordRequest request2 = new UpdateDomainRecordRequest();
					UpdateDomainRecordResponse response2;
					request2.setRecordId("63500319");
					request2.setrR("local");
					request2.setType("A");
					request2.setValue(ipLocal);
					try {
						response2 = client.execute(request2);
						System.out.println(response2.getBody());
						if (null == response2.getErrorCode() || "".equals(response2.getErrorCode())) {
							localIP = ipLocal;
						}

					} catch (ApiException e) {
						e.printStackTrace();
					}
				}

			}
			
			
			//解決莫名的假死，沒法debug，先這樣搞
			FutureTask<Void> futureTask = new FutureTask<Void>(new Callable<Void>() {

				@Override
				public Void call() throws Exception {
					{// 绑定外网Ip到smp

						String ip = getMyIP();

						System.out.println(ip);
						if (null != ip && !outIP.equals(ip) && ip != "" && ip.contains(".")) {
							UpdateDomainRecordRequest request2 = new UpdateDomainRecordRequest();
							UpdateDomainRecordResponse response2;
							request2.setRecordId("71948641");
							request2.setrR("smp");
							request2.setType("A");
							request2.setValue(ip);
							try {
								response2 = client.execute(request2);
								System.out.println(response2.getBody());
								if (null == response2.getErrorCode() || "".equals(response2.getErrorCode())) {
									outIP = ip;
								}
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}
					return null;
				}
			});
			executor.execute(futureTask);
			try {
				futureTask.get(30000, TimeUnit.MILLISECONDS);
			} catch (Exception e) {
				e.printStackTrace();
			}finally{
				futureTask.cancel(true);//此处一定好中止任务，不然线程会一直没有完成，占满single线程池
			}
			// 小睡30秒
			Thread.sleep(1000 * 30);

		}

	}

	/**
	 * 获取外网IP
	 * 
	 * @return
	 */
	private static String getMyIP() {
		InputStream ins = null;
		HttpURLConnection con =null;
		try {
			System.out.print(-1);
			con = (HttpURLConnection) url.openConnection();
			con.setConnectTimeout(15000);
			System.out.print(-2);
			
			con.setDoOutput(true);
			con.setDoInput(true);
			con.setUseCaches(false);
			
			System.out.print(1);
			ins = con.getInputStream();//此外会无限阻塞，ip138是什么B服务器？
			System.out.println(2);
			
			if(200!=con.getResponseCode()){
				return "";
			}
			
			InputStreamReader isReader = new InputStreamReader(ins, "GB2312");
			BufferedReader bReader = new BufferedReader(isReader);
			StringBuilder webContent = new StringBuilder();
			String str = null;
			while ((str = bReader.readLine()) != null) {
				webContent.append(str);
			}
			int start = webContent.indexOf("[") + 1;
			int end = webContent.indexOf("]");
			return webContent.substring(start, end);
		} catch (Exception e) {
			e.printStackTrace();
			return "";
		} finally {
			if (ins != null) {
				try {
					ins.close();
					con.disconnect();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	// 获取本地IP
	public static String getLocalIP() {
		String ip = "";
		try {
			ip = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
		}

		if (!ip.startsWith("127.")) {
			return ip;
		}
		// linux获取本地IP方法
		try {
			Enumeration<?> e1 = (Enumeration<?>) NetworkInterface.getNetworkInterfaces();
			while (e1.hasMoreElements()) {
				NetworkInterface ni = (NetworkInterface) e1.nextElement();
				if (!ni.getName().equals("eth0") && !ni.getName().equals("wlan0")) {
					continue;
				} else {
					Enumeration<?> e2 = ni.getInetAddresses();
					while (e2.hasMoreElements()) {
						InetAddress ia = (InetAddress) e2.nextElement();
						if (ia instanceof Inet6Address)
							continue;
						ip = ia.getHostAddress();
					}
					break;
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
		}
		return ip;
	}
}
