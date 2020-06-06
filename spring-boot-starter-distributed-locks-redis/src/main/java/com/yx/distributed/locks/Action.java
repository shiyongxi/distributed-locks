package com.yx.distributed.locks;

/**
 * Action
 *
 * @Auther: shiyongxi
 * @Date: 2020-06-02 15:21
 */
@FunctionalInterface
public interface Action {

    void execute();

}