package org.docksidestage.handson.exercise;

import javax.annotation.Resource;

import org.docksidestage.handson.dbflute.exbhv.MemberBhv;
import org.docksidestage.handson.unit.UnitContainerTestCase;

/**
 * @author r.iwata
 */
public class HandsOn02Test extends UnitContainerTestCase {

    // memo
    // memberBhvはmemberテーブルに対して操作できる
    // CBで検索条件を指定できる
    // https://dbflute.seasar.org/ja/manual/function/ormapper/behavior/select/selectcount.html
    @Resource
    private MemberBhv memberBhv;

    public void test_existsTestData() throws Exception {
        // ## Arrange ##

        // ## Act ##
        int count = memberBhv.selectCount(cb -> {});

        // ## Assert ##
        log("member count: {}", count);
        assertTrue(count > 0);
    }
}
