package com.lany.fivechess.net;

import static com.lany.fivechess.net.ConnectConstants.BROADCAST_EXIT;
import static com.lany.fivechess.net.ConnectConstants.BROADCAST_JOIN;
import static com.lany.fivechess.net.ConnectConstants.CHAT_ONE;
import static com.lany.fivechess.net.ConnectConstants.CONNECT_AGREE;
import static com.lany.fivechess.net.ConnectConstants.CONNECT_ASK;
import static com.lany.fivechess.net.ConnectConstants.CONNECT_REJECT;
import static com.lany.fivechess.net.ConnectConstants.MULTICAST_ERROR;
import static com.lany.fivechess.net.ConnectConstants.ON_EXIT;
import static com.lany.fivechess.net.ConnectConstants.ON_JOIN;
import static com.lany.fivechess.net.ConnectConstants.SOCKET_NULL;
import static com.lany.fivechess.net.ConnectConstants.UDP_DATA_ERROR;
import static com.lany.fivechess.net.ConnectConstants.UDP_IP_ERROR;
import static com.lany.fivechess.net.ConnectConstants.UDP_JOIN;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * �������<br>
 * ��ʼ���������󣬵���{@link #start()}�����ͻ����������� �ڵĿ������ֻ�ͬʱ�Լ�Ҳ���Ϊ���˿ɼ�Ķ���<br>
 * ������������������᷵�ؿ�������Ļ������IP��ַ��
 */
public class ConnnectingService {
	public static final String TAG = "ConnnectManager";
	private static final boolean DEBUG = true;

	private String mIp;

	// UPD���ճ���
	private DatagramSocket mDataSocket;
	// ��Զ�㲥
	private MulticastSocket mMulticastSocket;
	private InetAddress mCastAddress;

	// �㲥���ַ
	private static final String MUL_IP = "230.0.2.2";
	private static final int MUL_PORT = 1688;
	private static final int UDP_PORT = 2599;

	// ����UPD��Ϣ
	private UDPReceiver mUdpReceiver;
	// ���չ㲥��Ϣ
	private MulticastReceiver mMulticastReceiver;
	// udp��Ϣ����ģ��
	private UdpSendHandler mUdpSender;
	// �㲥��Ϣ����ģ��
	private MulticastSendHandler mBroadCastSender;

	private Handler mRequestHandler;

	public ConnnectingService(String ip, Handler request) {
		mRequestHandler = request;
		this.mIp = ip;
		mUdpReceiver = new UDPReceiver();
		mMulticastReceiver = new MulticastReceiver();
	}

	/**
	 * �������ӳ���
	 */
	public void start() {
		mUdpReceiver.start();
		mMulticastReceiver.start();

		HandlerThread udpThread = new HandlerThread("udpSender");
		udpThread.start();
		mUdpSender = new UdpSendHandler(udpThread.getLooper());

		HandlerThread broadcastThread = new HandlerThread("broadcastSender");
		broadcastThread.start();
		mBroadCastSender = new MulticastSendHandler(broadcastThread.getLooper());
	}

	public void stop() {
		mUdpReceiver.quit();
		mMulticastReceiver.quit();
		mUdpSender.getLooper().quit();
		mBroadCastSender.getLooper().quit();
	}

	/**
	 * ����һ����ѯ�㲥��Ϣ����ѯ��ǰ�����Ӷ���
	 */
	public void sendScanMsg() {
		Message msg = Message.obtain();
		byte[] buf = packageBroadcast(BROADCAST_JOIN);
		msg.obj = buf;
		mBroadCastSender.sendMessage(msg);

	}

	/**
	 * ����һ����ѯ�㲥��Ϣ���˳�������
	 */
	public void sendExitMsg() {
		// ��һ���̷߳���һ��������㲥(android���̲߳������������)
		// ����mMulticastSocket������ʱ��Ϊ�˳���ʱ���漰���̲߳���
		// ����mMulticastSocket�Ѿ�close״̬�����ɿ�
		new Thread() {
			@Override
			public void run() {
				MulticastSocket multicastSocket;
				try {
					multicastSocket = new MulticastSocket();
					InetAddress address = InetAddress.getByName(MUL_IP);
					multicastSocket.setTimeToLive(1);
					byte[] buf = packageBroadcast(BROADCAST_EXIT);
					DatagramPacket datagramPacket = new DatagramPacket(buf,
							buf.length);
					// ���յ�ַ��group�ı�ʶ��ͬ
					datagramPacket.setAddress(address);
					// �������Ķ˿ں�
					datagramPacket.setPort(MUL_PORT);
					multicastSocket.send(datagramPacket);
					multicastSocket.close();
				} catch (IOException e) {
					Log.d(TAG, "send exit multicast fail:" + e.getMessage());
				}
			}
		}.start();
	}

	/**
	 * ��������������Ϣ
	 * 
	 * @param ipDst
	 */
	public void sendAskConnect(String ipDst) {
		Message msg = Message.obtain();
		Bundle b = new Bundle();
		b.putString("ipDst", ipDst);
		byte[] data = createAskConnect();
		b.putByteArray("data", data);
		msg.setData(b);
		mUdpSender.sendMessage(msg);
	}

	/**
	 * ������������
	 * 
	 * @param content
	 * @param ipDst
	 */
	public void sendChat(String content, String ipDst) {
		Message msg = Message.obtain();
		Bundle b = new Bundle();
		b.putString("ipDst", ipDst);
		byte[] data = createChat(content);
		b.putByteArray("data", data);
		msg.setData(b);
		mUdpSender.sendMessage(msg);
	}

	/**
	 * ͬ������
	 */
	public void accept(String ipDst) {
		Message msg = Message.obtain();
		Bundle b = new Bundle();
		b.putString("ipDst", ipDst);
		byte[] data = createConnectResponse(CONNECT_AGREE);
		b.putByteArray("data", data);
		msg.setData(b);
		mUdpSender.sendMessage(msg);
	}

	/**
	 * �ܾ�����
	 */
	public void reject(String ipDst) {
		Message msg = Message.obtain();
		Bundle b = new Bundle();
		b.putString("ipDst", ipDst);
		byte[] data = createConnectResponse(CONNECT_REJECT);
		b.putByteArray("data", data);
		msg.setData(b);
		mUdpSender.sendMessage(msg);
	}

	/**
	 * ����UDP��Ϣ,δ����TCP����֮ǰ����ͨ��udp������Ϣ
	 * 
	 * @author qingc
	 *
	 */
	class UDPReceiver extends Thread {

		byte[] buf = new byte[1024];
		boolean isInit = true;

		private DatagramSocket dataSocket;
		private DatagramPacket dataPacket;

		public UDPReceiver() {
			try {
				dataSocket = new DatagramSocket(UDP_PORT);
				mDataSocket = dataSocket;
				dataPacket = new DatagramPacket(buf, buf.length);
			} catch (SocketException e) {
				isInit = false;
				Log.d(TAG, "Socket Exception:" + e.getMessage());
			}
		}

		@Override
		public void run() {
			try {
				while (isInit) {
					mDataSocket.receive(dataPacket);
					if (DEBUG) {
						Log.d(TAG, "udp received:" + Arrays.toString(buf));
					}
					int type = buf[0];
					// ��buffer�н�ȡ�յ������
					byte[] body = new byte[dataPacket.getLength() - 1];
					System.arraycopy(buf, 1, body, 0,
							dataPacket.getLength() - 1);
					switch (type) {
					case UDP_JOIN:
						processUdpJoin(body);
						break;
					case CONNECT_ASK:
						processAsk(body);
						break;
					case CHAT_ONE:
						processChat(body);
						break;
					case CONNECT_AGREE:
					case CONNECT_REJECT:
						processConnectResponse(body, type);
					default:
						break;
					}

				}
			} catch (SocketException e) {
				isInit = false;
				Log.d(TAG, "Socket Exception:" + e.getMessage());
			} catch (IOException e) {
				isInit = false;
				Log.d(TAG, "IOException:"
						+ "an error occurs while receiving the packet");
			}
		}

		public void quit() {
			dataSocket.close();
			isInit = false;
		}

	}

	/**
	 * ����UDP��Ϣ��δ����TCP����֮ǰ����ͨ��UDP����ָ��ƶ���ip
	 */
	class UdpSendHandler extends Handler {

		public UdpSendHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			Bundle b = msg.peekData();
			String ipDst = b.getString("ipDst");
			byte[] data = b.getByteArray("data");
			Log.d(TAG, "udp send destination ip:" + ipDst);
			if (DEBUG) {
				Log.d(TAG, "udp send data:" + Arrays.toString(data));
			}
			if (data == null) {
				onError(UDP_DATA_ERROR);
			}
			try {
				DatagramSocket ds;
				ds = mDataSocket;
				if (ds == null) {
					onError(SOCKET_NULL);
					return;
				}
				InetAddress dstAddress = InetAddress.getByName(ipDst);

				// ����������ݰ�
				DatagramPacket dataPacket = new DatagramPacket(data,
						data.length, dstAddress, UDP_PORT);
				ds.send(dataPacket);
			} catch (UnknownHostException e1) {
				Log.d(TAG, "ip is not corrected");
				onError(UDP_IP_ERROR);
			} catch (IOException e) {
				Log.d(TAG, "udp socket error:" + e.getMessage());
			}

		}

		public void quit() {
			getLooper().quit();
		}

	}

	/**
	 * ���չ㲥��Ϣ�̣߳����������ֻ��ɨ������㲥
	 *
	 */
	class MulticastReceiver extends Thread {

		byte[] buffer = new byte[1024];
		private boolean isInit = true;

		private MulticastSocket multiSocket;
		private DatagramPacket dataPacket;

		public MulticastReceiver() {
			try {
				multiSocket = new MulticastSocket();
				// �������ʱ��Ҫָ������Ķ˿ں�
				multiSocket = new MulticastSocket(MUL_PORT);
				// ����㲥��
				InetAddress address = InetAddress.getByName(MUL_IP);
				mCastAddress = address;
				multiSocket.joinGroup(address);
				multiSocket.setTimeToLive(1);
				dataPacket = new DatagramPacket(buffer, buffer.length);
				// ȫ������ָ������Ĺ㲥socket,���ڷ��͹㲥��Ϣ
				mMulticastSocket = multiSocket;
			} catch (IOException e) {
				isInit = false;
				Log.d(TAG, "Init mulcast fail by IOException=" + e.getMessage());
			}
		}

		@Override
		public void run() {
			try {
				while (isInit) {
					// ������ݣ����������״̬
					mMulticastSocket.receive(dataPacket);
					// ��buffer�н�ȡ�յ������
					byte[] message = new byte[dataPacket.getLength()];
					System.arraycopy(buffer, 0, message, 0,
							dataPacket.getLength());
					Log.d(TAG, "multicast receive:" + Arrays.toString(message));
					String ip = processBroadcast(message);
					// Check ip address and send ip address myself to it.
					if (ip != null && !ip.equals(mIp)) {
						Message msg = Message.obtain();
						Bundle b = new Bundle();
						b.putString("ipDst", ip);
						byte[] data = packageUdpJoin();
						b.putByteArray("data", data);
						msg.setData(b);
						mUdpSender.sendMessage(msg);
					}
				}
			} catch (IOException e) {
				Log.d(TAG, "IOException=" + e.getMessage());
			}
		}

		public void quit() {
			// close socket
			multiSocket.close();
			isInit = false;
		}

	}

	/**
	 * ���͹㲥��Ϣ
	 */
	class MulticastSendHandler extends Handler {

		public MulticastSendHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {

			byte[] buf = (byte[]) msg.obj;
			if (DEBUG) {
				Log.d(TAG, "BroadcastSendHandler:data=" + buf);
			}
			MulticastSocket s = mMulticastSocket;
			if (s == null) {
				onError(SOCKET_NULL);
				return;
			}
			InetAddress address = mCastAddress;
			if (address == null || !address.isMulticastAddress()) {
				onError(MULTICAST_ERROR);
				return;
			}
			try {
				// s.setTimeToLive(1); is it nessary?
				DatagramPacket datagramPacket = new DatagramPacket(buf,
						buf.length);
				// ���÷���group��ַ
				datagramPacket.setAddress(address);
				// �������Ķ˿ں�
				datagramPacket.setPort(MUL_PORT);
				s.send(datagramPacket);
			} catch (IOException e) {
				Log.d(TAG, "send multicasr fail:" + e.getMessage());
				onError(SOCKET_NULL);
			}
		}

		public void quit() {
			getLooper().quit();
		}

	}

	/**
	 * ������Ϣ
	 * 
	 * @param error
	 */
	private void onError(int error) {
		Log.d(TAG, "error:" + error);
		Message msg = Message.obtain();
		msg.what = error;
		mRequestHandler.sendMessage(msg);
	}

	/**
	 * ���µĿ�����������
	 * 
	 * @param name
	 *            ������
	 * @param ip
	 *            ��ַ
	 */
	private void onJoin(String name, String ip) {
		Message msg = Message.obtain();
		msg.what = ON_JOIN;
		Bundle b = new Bundle();
		b.putString("name", name);
		b.putString("ip", ip);
		msg.setData(b);
		mRequestHandler.sendMessage(msg);
	}

	/**
	 * �п���������˳�
	 * 
	 * @param name
	 *            ������
	 * @param ip
	 *            ��ַ
	 */
	private void onExit(String name, String ip) {
		Message msg = Message.obtain();
		msg.what = ON_EXIT;
		Bundle b = new Bundle();
		b.putString("name", name);
		b.putString("ip", ip);
		msg.setData(b);
		mRequestHandler.sendMessage(msg);
	}

	/**
	 * ����UDP��������Ӷ�����Ϣ
	 * 
	 * @param data
	 *            ���յ�����Ϣ��
	 * @return ���ؽ�������ip��ַ
	 */
	private void processUdpJoin(byte[] data) {
		int nameLen = data[0];
		int ipLen = data[nameLen + 1];
		byte[] nameArr = new byte[nameLen];
		byte[] iparr = new byte[ipLen];
		System.arraycopy(data, 1, nameArr, 0, nameLen);
		System.arraycopy(data, nameLen + 2, iparr, 0, ipLen);
		String name = new String(nameArr);
		String ip = new String(iparr);
		Log.d(TAG, "processUdpJoin-->" + "name=" + name + "  ip=" + ip);
		onJoin(name, ip);
	}

	/**
	 * ��������ƺ�ip��ַ��װ��byte����
	 * 
	 * @return
	 */
	private byte[] packageUdpJoin() {
		byte[] ip = mIp.getBytes();
		byte[] name = (Build.BRAND + "-" + Build.MODEL).getBytes();
		// ��Ϣ���Ȱ���(���֡����ֳ��ȡ�ip��ip���ȡ��㲥����)
		int dataLen = name.length + ip.length + 3;
		byte[] data = new byte[dataLen];
		data[0] = UDP_JOIN;
		int namePos = 1;
		int ipPos = namePos + name.length + 1;
		data[namePos] = (byte) name.length;
		System.arraycopy(name, 0, data, namePos + 1, name.length);
		data[ipPos] = (byte) ip.length;
		System.arraycopy(ip, 0, data, ipPos + 1, ip.length);
		return data;
	}

	/**
	 * ����㲥��Ϣ
	 * 
	 * @param data
	 *            ���յ�����Ϣ��
	 * @return ���ؽ�������ip��ַ
	 */
	private String processBroadcast(byte[] data) {
		int nameLen = data[0];
		int ipLen = data[nameLen + 1];
		byte[] nameArr = new byte[nameLen];
		byte[] iparr = new byte[ipLen];
		System.arraycopy(data, 1, nameArr, 0, nameLen);
		System.arraycopy(data, nameLen + 2, iparr, 0, ipLen);
		String name = new String(nameArr);
		String ip = new String(iparr);
		Log.d(TAG, "processBroadcast-->" + "name=" + name + "  ip=" + ip);
		// ������Լ����͵���Ϣ���򲻼�������Ӽ���
		if (ip.equals(mIp)) {
			return ip;
		}
		int type = data[data.length - 1];
		if (type == BROADCAST_JOIN) {
			onJoin(name, ip);
		} else if (type == BROADCAST_EXIT) {
			onExit(name, ip);
		}
		return ip;
	}

	/**
	 * ��������ƺ�ip��ַ��װ��byte����
	 * 
	 * @return
	 */
	private byte[] packageBroadcast(int type) {
		byte[] ip = mIp.getBytes();
		byte[] name = (Build.BRAND + "-" + Build.MODEL).getBytes();
		// ��Ϣ���Ȱ���(���֡����ֳ��ȡ�ip��ip���ȡ��㲥����)
		int dataLen = name.length + 1 + ip.length + 1 + 1;
		byte[] data = new byte[dataLen];
		int namePos = 0;
		int ipPos = name.length + 1;
		data[namePos] = (byte) name.length;
		System.arraycopy(name, 0, data, namePos + 1, name.length);
		data[ipPos] = (byte) ip.length;
		System.arraycopy(ip, 0, data, ipPos + 1, ip.length);
		data[dataLen - 1] = (byte) type;
		return data;
	}

	/**
	 * ��װ����������Ϣ��
	 * 
	 * @return data
	 */
	private byte[] createAskConnect() {
		byte[] ip = mIp.getBytes();
		byte[] name = (Build.BRAND + "-" + Build.MODEL).getBytes();
		// ��Ϣ���Ȱ���(���֡����ֳ��ȡ�ip��ip���ȡ��㲥����)
		int dataLen = name.length + ip.length + 3;
		byte[] data = new byte[dataLen];
		data[0] = CONNECT_ASK;
		int namePos = 1;
		int ipPos = namePos + name.length + 1;
		data[namePos] = (byte) name.length;
		System.arraycopy(name, 0, data, namePos + 1, name.length);
		data[ipPos] = (byte) ip.length;
		System.arraycopy(ip, 0, data, ipPos + 1, ip.length);
		return data;
	}

	/**
	 * ���������������
	 * 
	 * @param data
	 */
	private void processAsk(byte[] data) {
		int nameLen = data[0];
		int ipLen = data[nameLen + 1];
		byte[] nameArr = new byte[nameLen];
		byte[] iparr = new byte[ipLen];
		System.arraycopy(data, 1, nameArr, 0, nameLen);
		System.arraycopy(data, nameLen + 2, iparr, 0, ipLen);
		String name = new String(nameArr);
		String ip = new String(iparr);
		Log.d(TAG, "processUdpJoin-->" + "name=" + name + "  ip=" + ip);
		Message msg = Message.obtain();
		msg.what = CONNECT_ASK;
		Bundle b = new Bundle();
		b.putString("name", name);
		b.putString("ip", ip);
		msg.setData(b);
		mRequestHandler.sendMessage(msg);
	}

	/**
	 * ��������������Ϣ��
	 * 
	 * @return
	 */
	private byte[] createChat(String content) {
		byte[] ip = mIp.getBytes();
		byte[] name = (Build.BRAND).getBytes();
		byte[] chat = content.getBytes();
		// ��Ϣ���Ȱ���(���֡����ֳ��ȡ�ip��ip���ȡ��㲥���͡��������ݡ����쳤��)
		int dataLen = name.length + ip.length + 3 + chat.length + 1;
		byte[] data = new byte[dataLen];
		data[0] = CHAT_ONE;
		int namePos = 1;
		int ipPos = namePos + name.length + 1;
		int chatPos = ipPos + ip.length + 1;
		data[namePos] = (byte) name.length;
		System.arraycopy(name, 0, data, namePos + 1, name.length);
		data[ipPos] = (byte) ip.length;
		System.arraycopy(ip, 0, data, ipPos + 1, ip.length);
		data[chatPos] = (byte) chat.length;
		System.arraycopy(chat, 0, data, chatPos + 1, chat.length);
		return data;
	}

	/**
	 * ������������
	 * 
	 * @param data
	 */
	private void processChat(byte[] data) {
		int nameLen = data[0];
		int ipLen = data[nameLen + 1];
		int chatLen = data[nameLen + ipLen + 2];
		byte[] nameArr = new byte[nameLen];
		byte[] iparr = new byte[ipLen];
		byte[] chatArr = new byte[chatLen];
		System.arraycopy(data, 1, nameArr, 0, nameLen);
		System.arraycopy(data, nameLen + 2, iparr, 0, ipLen);
		System.arraycopy(data, nameLen + ipLen + 3, chatArr, 0, chatLen);
		String name = new String(nameArr);
		String ip = new String(iparr);
		String chat = new String(chatArr);
		Log.d(TAG, "processChat-->" + "name=" + name + "  ip=" + ip + "  chat="
				+ chat);

		Message msg = Message.obtain();
		msg.what = CHAT_ONE;
		Bundle b = new Bundle();
		b.putString("name", name);
		b.putString("ip", ip);
		b.putString("chat", chat);
		msg.setData(b);
		mRequestHandler.sendMessage(msg);
	}

	/**
	 * ����������Ӧ��Ϣ
	 * 
	 * @param type
	 * @return ��Ϣ����
	 */
	private byte[] createConnectResponse(int type) {
		byte[] ip = mIp.getBytes();
		byte[] name = (Build.BRAND).getBytes();
		// ��Ϣ���Ȱ���(���֡����ֳ��ȡ�ip��ip���ȡ��㲥����)
		int dataLen = name.length + ip.length + 3;
		byte[] data = new byte[dataLen];
		data[0] = (byte) type;
		int namePos = 1;
		int ipPos = namePos + name.length + 1;
		data[namePos] = (byte) name.length;
		System.arraycopy(name, 0, data, namePos + 1, name.length);
		data[ipPos] = (byte) ip.length;
		System.arraycopy(ip, 0, data, ipPos + 1, ip.length);
		return data;
	}

	/**
	 * ��������������Ӧ������
	 * 
	 * @param data
	 * @param type
	 */
	private void processConnectResponse(byte[] data, int type) {
		int nameLen = data[0];
		int ipLen = data[nameLen + 1];
		byte[] nameArr = new byte[nameLen];
		byte[] iparr = new byte[ipLen];
		System.arraycopy(data, 1, nameArr, 0, nameLen);
		System.arraycopy(data, nameLen + 2, iparr, 0, ipLen);
		String name = new String(nameArr);
		String ip = new String(iparr);
		Log.d(TAG, "processConnectResponse-->" + "name=" + name + "  ip=" + ip);
		Message msg = Message.obtain();
		msg.what = type;
		Bundle b = new Bundle();
		b.putString("name", name);
		b.putString("ip", ip);
		msg.setData(b);
		mRequestHandler.sendMessage(msg);
	}
}
