/**
 * Copyright 2015-2018 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codesourcery.javr.ui;

import java.util.concurrent.ArrayBlockingQueue;

import javax.swing.SwingUtilities;

import org.apache.log4j.Logger;

/**
 * Helper to run long-running tasks in the background and afterwards execute 
 * a success/error callback function on the Swing EDT.
 * 
 * @author tobias.gierke@voipfuture.com
 */
public class BackgroundThread 
{
	private static final Logger LOG = Logger.getLogger(BackgroundThread.class);
	
	private static final BackgroundThread INSTANCE=new BackgroundThread();
	
	private final ArrayBlockingQueue<QueueItem> workQueue = new ArrayBlockingQueue<>( 100,  true );
	
	private static final class QueueItem 
	{
		public final ICallback task;
		public final ICallback successCallback;
		public final ICallback errorCallback;
		
		public QueueItem(ICallback task, ICallback successCallback, ICallback errorCallback) 
		{
			this.task = task;
			this.successCallback = successCallback;
			this.errorCallback = errorCallback;
		}
	}
	
	public static interface ICallback 
	{
		public void run() throws Exception;
	}
	
	private final class Executor extends Thread 
	{
		public Executor() {
			setName("background-executor");
			setDaemon(true);
		}
		
		private void runOnEDT(ICallback cb,String errorMsg) {
			
			SwingUtilities.invokeLater( () -> 
			{
				try {
					cb.run();
				} 
				catch (Exception e) 
				{
					LOG.error("run(): "+errorMsg,e);
				}
			});
		}
		
		@Override
		public void run() 
		{
			while( true ) 
			{
				final QueueItem item;
				try {
					item = workQueue.take();
				}
				catch (InterruptedException e) {
					e.printStackTrace();
					continue;
				}
				
				try {
					item.task.run();
				} 
				catch(Exception e) 
				{
					LOG.error("run(): Task "+item+" failed",e);
					if ( item.errorCallback != null ) 
					{
						runOnEDT( item.errorCallback , "Invoking error callback "+item.errorCallback+" of task "+item+" failed");
					}
					continue;
				}
				
				if ( item.successCallback != null ) 
				{
					runOnEDT( item.successCallback , "Invoking success callback "+item.successCallback+" of task "+item+" failed");
				}
			}
		}
	}
	private final Object executorLock=new Object();
	private Executor executor;
	
	public BackgroundThread getInstance() {
		return INSTANCE;
	}
	
	private void doWithExecutor(Runnable cb) 
	{
		synchronized( executorLock ) {
			if ( executor == null || ! executor.isAlive() ) {
				executor = new Executor();
				executor.start();
			}
		}
		cb.run();
	}
	
	/**
	 * Queue a background task
	 * @param runnable
	 */
	public void submit(ICallback runnable) 
	{
		submit(runnable,null,null);
	}
	
	/**
	 * Queue a background task and a callback to be run on success.
	 * 
	 * @param runnable
	 * @param successCallback callback to run on success, callback will get executed on the EDT
	 */
	public void submit(ICallback runnable,ICallback successCallback) 
	{
		submit(runnable,successCallback,null);
	}
	
	/**
	 * Queue a background task and a callback to be run on success.
	 * 
	 * @param runnable
	 * @param successCallback callback to run on success, callback will get executed on the EDT
	 * @param errorCallback callback to run on failure, callback will get executed on the EDT
	 */	
	public void submit(ICallback runnable,ICallback successCallback,ICallback errorCallback) 
	{
		doWithExecutor( () -> workQueue.add( new QueueItem(runnable,successCallback,errorCallback) ) );
	}
}