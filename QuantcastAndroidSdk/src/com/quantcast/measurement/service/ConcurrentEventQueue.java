/**
* Copyright 2012 Quantcast Corp.
*
* This software is licensed under the Quantcast Mobile App Measurement Terms of Service
* https://www.quantcast.com/learning-center/quantcast-terms/mobile-app-measurement-tos
* (the “License”). You may not use this file unless (1) you sign up for an account at
* https://www.quantcast.com and click your agreement to the License and (2) are in
*  compliance with the License. See the License for the specific language governing
* permissions and limitations under the License.
*
*/       
package com.quantcast.measurement.service;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.quantcast.measurement.event.Event;
import com.quantcast.measurement.event.EventManager;
import com.quantcast.measurement.event.EventQueue;

class ConcurrentEventQueue implements EventQueue {

    private static final long SLEEP_TIME_IN_MS = 500;
    private static final long UPLOAD_INTERVAL_IN_MS = 10 * 1000; // 10 seconds
    
    private static final String THREAD_NAME = ConcurrentEventQueue.class.getName();

    private volatile boolean continueThread;
    private final ConcurrentLinkedQueue<Event> events;
    private long nextUploadTime;

    public ConcurrentEventQueue(final EventManager manager) {
        continueThread = true;
        events = new ConcurrentLinkedQueue<Event>();
        setNextUploadTime();
        new Thread(new Runnable() {

            @Override
            public void run() {
                do {
                    try {
                        Thread.sleep(SLEEP_TIME_IN_MS);
                    }
                    catch (InterruptedException e) {
                        // Do nothing
                    }

                    boolean shouldForceUpload = false;
                    LinkedList<Event> eventsToSave = new LinkedList<Event>();
                    while (!events.isEmpty()) {
                        Event event = events.poll();
                        eventsToSave.add(event);
                        shouldForceUpload |= event.getEventType().shouldForceUpload();
                    }
                    manager.saveEvents(eventsToSave);
                    
                    if (shouldForceUpload || System.currentTimeMillis() >= nextUploadTime) {
                        setNextUploadTime();
                        manager.attemptEventsUpload(shouldForceUpload);
                    }
                } while(continueThread || !events.isEmpty());

                manager.destroy();
            }
        }, THREAD_NAME).start();
    }
    
    private void setNextUploadTime() {
        nextUploadTime = System.currentTimeMillis() + UPLOAD_INTERVAL_IN_MS;
    }

    @Override
    public void terminate() {
        continueThread = false;
    }

    @Override
    public void push(Event event) {
        events.add(event);
    }

}