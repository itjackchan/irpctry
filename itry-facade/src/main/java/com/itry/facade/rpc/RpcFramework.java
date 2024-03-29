/*
 * Copyright 2011 Alibaba.com All right reserved. This software is the
 * confidential and proprietary information of Alibaba.com ("Confidential
 * Information"). You shall not disclose such Confidential Information and shall
 * use it only in accordance with the terms of the license agreement you entered
 * into with Alibaba.com.
 */
package com.itry.facade.rpc;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * RpcFramework
 * 
 * RPC框架主要包括两大功能：一个用于服务端暴露服务，一个用于客户端引用服务。
 */
public class RpcFramework {
	/**
	 * @param service 服务实现
	 * @param port    服务端口
	 * @throws Exception
	 */
	public static void export(final Object service, int port) throws Exception {
		if (service == null)
			throw new IllegalArgumentException("service instance == null");
		if (port <= 0 || port > 65535)
			throw new IllegalArgumentException("Invalid port " + port);
		System.out.println("Export service " + service.getClass().getName() + " on port " + port);
		@SuppressWarnings("resource")
		ServerSocket server = new ServerSocket(port);
		for (;;) {// 无限循环，因为一直提供服务
			try {
				final Socket socket = server.accept();// 以阻塞的方式监听网络连接
				new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							try {
								ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
								try {
									String methodName = input.readUTF();
									Class<?>[] parameterTypes = (Class<?>[]) input.readObject();
									Object[] arguments = (Object[]) input.readObject();
									ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
									try {
										Method method = service.getClass().getMethod(methodName, parameterTypes);
										Object result = method.invoke(service, arguments);
										output.writeObject(result);
									} catch (Throwable t) {
										output.writeObject(t);
									} finally {
										output.close();
									}
								} finally {
									input.close();
								}
							} finally {
								socket.close();
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}).start();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * @param <T>            接口泛型
	 * @param interfaceClass 接口类型
	 * @param host           服务器主机名
	 * @param port           服务器端口
	 * @return 远程服务
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public static <T> T refer(final Class<T> interfaceClass, final String host, final int port) throws Exception {
		if (interfaceClass == null)
			throw new IllegalArgumentException("Interface class == null");
		if (!interfaceClass.isInterface())
			throw new IllegalArgumentException("The " + interfaceClass.getName() + " must be interface class!");
		if (host == null || host.length() == 0)
			throw new IllegalArgumentException("Host == null!");
		if (port <= 0 || port > 65535)
			throw new IllegalArgumentException("Invalid port " + port);
		System.out.println("Get remote service " + interfaceClass.getName() + " from server " + host + ":" + port);
		return (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class<?>[] { interfaceClass },
				new InvocationHandler() {
					public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
						Socket socket = new Socket(host, port);
						try {
							ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
							try {
								output.writeUTF(method.getName());
								output.writeObject(method.getParameterTypes());
								output.writeObject(arguments);
								ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
								try {
									Object result = input.readObject();
									if (result instanceof Throwable) {
										throw (Throwable) result;
									}
									return result;
								} finally {
									input.close();
								}
							} finally {
								output.close();
							}
						} finally {
							socket.close();
						}
					}
				});
	}
}