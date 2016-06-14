import org.jsoup.Jsoup;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.RList;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeoutException;

public abstract class EmailRecSend {
	public static boolean retry = false;
	private static Store store = null;
	private static final String host = "pop.sina.com";
	private static final String provider = "pop3";
	private static final String username;
	private static final String password;
	private static final String keyword;

	static {
		username = Configurator.getProperties("username");
		password = Configurator.getProperties("password");
		keyword = Configurator.getProperties("keyword");
	}


	public static List<Pair> ReceiveMail() throws REXPMismatchException, MessagingException {
		System.out.println("Receive email");
		List<Pair> res = new ArrayList<Pair>();
		Properties props = System.getProperties();
		String cin = "";
		Folder inbox = null;
		try {
			if (store == null) {
				// 连接到POP3服务器
				Session ss = Session.getDefaultInstance(props, null);
				// 向回话"请求"一个某种提供者的存储库，是一个POP3提供者
				System.out.println("Get store");
				store = ss.getStore(provider);
			}
			if (!store.isConnected()) {
				// 连接存储库，从而可以打开存储库中的文件夹，此时是面向IMAP的
				System.out.println("Connect to store");
				TimeoutConnect.timeoutConnect(store, host, username, password, 10 * 1000);
			}
			// 打开文件夹，此时是关闭的，只能对其进行删除或重命名，无法获取关闭文件夹的信息
			// 从存储库的默认文件夹INBOX中读取邮件
			System.out.println("Get inbox");
			inbox = store.getFolder("INBOX");
			if (inbox == null) {
				System.out.println("NO INBOX");
				System.exit(1);
			}
			// 打开文件夹，读取信息
			System.out.println("Open inbox");
			inbox.open(Folder.READ_WRITE);
			System.out.println("TOTAL EMAIL:" + inbox.getMessageCount());
			// 获取邮件服务器中的邮件
			Message[] messages = inbox.getMessages();
			String from = null;
			cin = null;
			for (int i = messages.length - 1; i >= 0; i--) {
				Set<String> codeset = new HashSet<String>();
				// 解析地址为字符串
				from = InternetAddress.toString(messages[i].getFrom());
				if (from != null) {
					cin = getChineseFrom(from);
				}
				String subject = messages[i].getSubject().trim();
				if (subject.toLowerCase().contains(keyword)) {
					StringBuffer errorcontent = new StringBuffer();
					StringBuffer content = new StringBuffer(30);
					getMailTextContent(messages[i], content);
					System.out.println("------------Message--" + (i + 1) + "------------");
					System.out.println("From:" + cin);
					if (subject != null) System.out.println("Subject:" + subject);
					System.out.println(content.toString());
					System.out.println("----------------------------");
					for (String part : content.toString().trim().split("\n")) {
						String[] slist = part.trim().split("[\\s\\r\\n]");
						for (String subq : slist) {
							if (subq.contains(":")) {
								String[] q = subq.split(":");
								try {
									if (q.length == 2 && q[0].length() == 6 && (!codeset.contains(q[0].trim()))) {
										codeset.add(q[0].trim());
										System.out.println("Find:" + q[0].trim() + "\t" + q[1].trim());
										res.add(new Pair(cin, q[0].trim(), Double.parseDouble(q[1].trim())));
									} else {
										errorcontent.append(subq).append("\n");
									}
								} catch (NumberFormatException e) {
									errorcontent.append(subq).append("\n");
								}
							} else {
								errorcontent.append(subq).append("\n");
							}
						}
					}
					if (errorcontent.toString().length() > 0) {
						System.out.println("Error content:" + errorcontent.toString());
						System.out.println("Report to:" + cin);
						SendEmail(cin, "格式错误:(正确格式为: 基金代码:预测净值)\n错误文本:" + errorcontent.toString() + "\n错误文本结束");
					}
					messages[i].setFlag(Flags.Flag.DELETED, true);
				} else {
					System.out.println("Unknown subject:" + subject.trim());
					messages[i].setFlag(Flags.Flag.DELETED, true);
				}
			}
			retry = false;
		} catch (javax.mail.AuthenticationFailedException e) {
			System.out.println("Authentication failed");
			try {
				Thread.currentThread().sleep(60 * 1000);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			retry = true;
		} catch (TimeoutException e) {
			retry = true;
		} catch (Exception e) {
			SendEmail(cin, e.getMessage());
			e.printStackTrace(System.err);
			System.out.println("Need retry");
			retry = true;
		} finally {
			try {
				System.out.println("Email check done");
				if (inbox != null) {
					inbox.close(true);
				}
			} catch (Exception e) {
				e.printStackTrace(System.err);
			}
		}
		return res;
	}

	public static void SendEmail(String to, String errormessage) {
		try {
			StringBuffer sb = new StringBuffer();
			sb.append(errormessage);
			sb.append("\n");

			Properties props = new Properties();
			// 开启debug调试
			props.setProperty("mail.debug", "false");
			// 发送服务器需要身份验证
			props.setProperty("mail.smtp.auth", "true");
			// 设置邮件服务器主机名
			props.setProperty("mail.host", "smtp.sina.com");
			// 发送邮件协议名称
			props.setProperty("mail.transport.protocol", "smtp");

			// 设置环境信息
			Session session = Session.getInstance(props);

			// 创建邮件对象
			Message msg = new MimeMessage(session);
			msg.setSubject("NEUFUND预测");
			// 设置邮件内容
			msg.setText(sb.toString());
			// 设置发件人
			msg.setFrom(new InternetAddress(username + "@sina.com"));

			Transport transport = session.getTransport();
			// 连接邮件服务器
			transport.connect(username, password);
			// 发送邮件
			transport.sendMessage(msg, new Address[]{new InternetAddress(to)});
			// 关闭连接
			transport.close();
		} catch (javax.mail.internet.AddressException e) {
			System.out.println("[ERROR-EMAIL-ADDRESS]" + to);
		} catch (MessagingException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void SendEmail(String to, String code, RList pres) throws REXPMismatchException, MessagingException {
		StringBuffer sb = new StringBuffer();
		try {
			sb.append("基金代码:" + pres.at(0).asString()).append("\n").append("2015年1月至今收益率:" + pres.at(1).asDouble())
					.append("\n交易策略(1买入2卖出0不操作):");
			for (int i : pres.at(2).asIntegers()) {
				sb.append(i + " ");
			}
		} catch (NullPointerException e) {
			sb.append("基金代码不存在:" + code);
		}
		sb.append("\n");
		try {
			Properties props = new Properties();
			// 开启debug调试
			props.setProperty("mail.debug", "false");
			// 发送服务器需要身份验证
			props.setProperty("mail.smtp.auth", "true");
			// 设置邮件服务器主机名
			props.setProperty("mail.host", "smtp.sina.com");
			// 发送邮件协议名称
			props.setProperty("mail.transport.protocol", "smtp");

			// 设置环境信息
			Session session = Session.getInstance(props);

			// 创建邮件对象
			Message msg = new MimeMessage(session);
			msg.setSubject("NEUFUND预测");
			// 设置邮件内容
			msg.setText(sb.toString());
			// 设置发件人
			msg.setFrom(new InternetAddress(username + "@sina.com"));

			Transport transport = session.getTransport();
			// 连接邮件服务器
			transport.connect(username, password);
			// 发送邮件
			transport.sendMessage(msg, new Address[]{new InternetAddress(to)});
			// 关闭连接
			transport.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// 解决中文乱码问题
	public static String getChineseFrom(String res) {
		String from = res;
		try {
			if (from.startsWith("=?GB") || from.startsWith("=?gb") || from.startsWith("=?UTF")) {
				from = MimeUtility.decodeText(from);
			} else {
				from = new String(from.getBytes("ISO8859_1"), "GBK");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return from;
	}

	public static void getMailTextContent(Message message, StringBuffer result) throws MessagingException,
			IOException {
		/*
		 * boolean isContainTextAttach = part.getContentType().indexOf("name") >
		 * 0; if (part.isMimeType("text/*") && !isContainTextAttach) {
		 * result.append(part.getContent().toString()); } else if
		 * (part.isMimeType("message/rfc822")) { getMailTextContent((Part)
		 * part.getContent(), result); } else if
		 * (part.isMimeType("multipart/*")) { Multipart multipart = (Multipart)
		 * part.getContent(); int partCount = multipart.getCount(); for (int i =
		 * 0; i < partCount; i++) { BodyPart bodyPart =
		 * multipart.getBodyPart(i); getMailTextContent(bodyPart, result);
		 * result.append("\n"); } }
		 */

		if (message instanceof MimeMessage) {
			MimeMessage m = (MimeMessage) message;
			Object contentObject = m.getContent();
			if (contentObject instanceof Multipart) {
				BodyPart clearTextPart = null;
				BodyPart htmlTextPart = null;
				Multipart content = (Multipart) contentObject;
				int count = content.getCount();
				for (int i = 0; i < count; i++) {
					BodyPart part = content.getBodyPart(i);
					if (part.isMimeType("text/plain")) {
						clearTextPart = part;
						break;
					} else if (part.isMimeType("text/html")) {
						htmlTextPart = part;
					}
				}

				if (clearTextPart != null) {
					result.append(clearTextPart.getContent().toString()).append("\n");
				} else if (htmlTextPart != null) {
					String html = (String) htmlTextPart.getContent();
					result.append(Jsoup.parse(html).text()).append("\n");
				}

			} else if (contentObject instanceof String) // a simple text message
			{
				result.append(contentObject).append("\n");
			}
		} else // not a mime message
		{
			result.append("无法识别邮件正文内容");
		}

	}
}
