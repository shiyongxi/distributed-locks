package com.yx.distributed.locks.controller;

import com.yx.distributed.locks.DistributedLockManager;
import com.yx.distributed.locks.Lock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Auther: shiyongxi
 * @Date: 2020-05-28 17:02
 * @Description: DemoController
 */
@RestController
@RequestMapping("/demo")
@Slf4j
public class DemoController {
    @Autowired
    private DistributedLockManager distributedLockManager;

    @RequestMapping("/test")
    public void set(@RequestParam("key") String key) {
        int count = 0;

        for (int i = 0; i < 100; i++) {
            count ++;

            final int tmp = count;
            new Thread() {
                @Override
                public void run() {
                    final Lock lock = distributedLockManager.acquire(key);

                    lock.lock();

                    log.info("count is {}, current time is {}", tmp, System.currentTimeMillis());

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    lock.unlock();
                }
            }.start();
        }
    }
}
