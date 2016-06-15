import org.rosuda.REngine.REXP;
import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.REngineException;
import org.rosuda.REngine.RList;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

import javax.mail.MessagingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class RemoteR {
	public static RConnection rConnection = null;

	public static List<RList> predict(List<Pair> pairList) throws RserveException, REXPMismatchException {
		List<RList> reslist = new ArrayList<RList>();
		if (pairList.size() > 0) {
			initR();
		}
		for (Pair p : pairList) {
			System.out.println("Predict-->Code:" + p.code + "\tPrice:" + p.price);
			REXP res = null;
			try {
				res = rConnection.parseAndEval("try(neunet.jpredict('" + p.code + "'," + p.price + "),silent=TRUE)");
				if (res.inherits("try-error")) {
					System.err.println("RError: " + res.asString());
					reslist.add(null);
					continue;
				} else {
					reslist.add(res.asList());
					continue;
				}
			} catch (org.rosuda.REngine.Rserve.RserveException e) {
				System.err.println("Prediction Error:" + p.code + "\t" + p.price);
				e.printStackTrace(System.err);
			} catch (REngineException e) {
				e.printStackTrace(System.err);
			}
			reslist.add(null);
			continue;
		}
		if (rConnection != null) {
			rConnection.close();
		}
		return reslist;
	}

	public static void initR() throws RserveException, REXPMismatchException {
		rConnection = new RConnection("127.0.0.1");
		rConnection.voidEval("setwd('/home/yiwen/neuFund')");
		System.out.println(rConnection.eval("getwd()").asString());
		rConnection.voidEval("source('neunet.run.R')");
		rConnection.voidEval("neunet.load_data_set(minlength=60)");
		rConnection.voidEval("neunet.load_model('ga_350_auto.RData')");
	}

	public static void main(String[] args) throws RserveException, REXPMismatchException, MessagingException {
		if (args.length < 1) {
			System.out.println("Usage:COMMAND conf");
			System.exit(1);
		} else {
			Configurator.readProperties(args[0]);
		}
		boolean first;
		while (true) {
			first = true;
			while (first || EmailRecSend.retry) {
				reportCurrentTime();
				first = false;
				List<Pair> res = EmailRecSend.ReceiveMail();
				if (EmailRecSend.retry) {
					continue;
				}
				List<RList> pres = predict(res);
				int i = 0;
				for (RList rl : pres) {
					EmailRecSend.SendEmail(res.get(i).from, res.get(i).code, rl);
					i++;
				}
				if (EmailRecSend.retry) {
					towait(Long.parseLong(Configurator.getProperties("retry_time")));
				}
			}
			towait(Long.parseLong(Configurator.getProperties("wait_time")));
		}
	}

	public static void towait(long t) {
		try {
			Thread.sleep(t);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public static String reportCurrentTime() {
		SimpleDateFormat sDateFormat = new SimpleDateFormat("HH:mm");
		String date = sDateFormat.format(new java.util.Date());
		System.out.println(date);
		return date;
	}
}
