package org.docksidestage.handson.exercise;

import javax.annotation.Resource;

import org.dbflute.cbean.result.ListResultBean;
import org.docksidestage.handson.dbflute.exbhv.MemberBhv;
import org.docksidestage.handson.dbflute.exentity.Member;
import org.docksidestage.handson.unit.UnitContainerTestCase;

// #1on1: Maven/Gradleの役割 (2026/05/01)
// o ダウンロード
// o 推移的依存のバージョン解決
// 推移的依存のコンフリクトの時の話も。
/*
e.g. pom.xml concept model @Model
                  Java API (Stringとか) ←こいつだけじゃ足りない！
  E                
  c      ... <--(Download) sea-1.1.jar -> mystic-2.1.jar -> hangar-0.8.jar
  l  M                         ↓       -> bigband-3.4.jar
  i  2                     sea-1.5.jar -> mystic-2.4.jar -> hangar-1.7.jar
  p  E-(直参照)-+                       -> bigband-3.7.jar -> piari-1.3.jar
  s          +-+
  e          |   あ  o     land-3.2.jar -> oneman-1.8.jar -> bigband-3.5.jar
  ↓          |   ぷ /|\
.classpath   |   り /\     piari-1.0.jar -> dstore-2.0.jar -> oneman-2.5.jar
  ↑          +-↓
  |           <<< pom.xml >>> ← sea と land と piari が欲しいぞと書いてある
  |                   ↑
  |                  M a v e n   ------------------+
  +-(自動生成)--   (eclipse-plugin)                 ↓↓
                                                Mavenセントラルサーバー
                                                +------------------+
                              o                 | sea.jar pom.xml  |
                             /|\ ---upload----> | land.jar pom.xml |
                             /\                 +------------------+
                           OSS開発者
 */
// 推移的依存どうやって調べる？
// オフィシャルサイト？githubのpom.xml？
// OSS開発者がMavenセントラルサーバーにアップしている。
// Mavenセントラルサーバーの負荷すごそう by いわたさん
// jcenterの夢破れたり
// GradleもMavenセントラルサーバーを使う
// Kotlin/ScalaのJavaみたいな感じ？
// ソフトとインフラ、漠然とGradleとかJavaとか言っても、ソフトとインフラ面がある。
// ソフトとしてのMaven/Gradleの違うの特徴。
// 自由度と統一性のジレンマ。
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

    public void test_テストデータが存在すること() throws Exception {
        // ## Arrange ##

        // ## Act ##
        int count = memberBhv.selectCount(cb -> {});

        // ## Assert ##
        log("member count: {}", count);
        assertTrue(count > 0);
    }

    // memo
    // https://dbflute.seasar.org/ja/manual/function/ormapper/behavior/select/selectlist.html#override
    public void test_会員名称がSで始まる会員を検索できること() throws Exception {
        // ## Arrange ##

        // ## Act ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.query().setMemberName_LikeSearch("S", op -> op.likePrefix());
            cb.query().addOrderBy_MemberName_Asc();
        });
    
        // ## Assert ##
        assertHasAnyElement(memberList);
        memberList.forEach(member -> {
            String memberName = member.getMemberName();
            log("memberName: {}", memberName);
            assertTrue(memberName.startsWith("S"));
        });
    }

    // memo
    // selectXXで補完した後は_llでさらに補完
    // acceptPKは主キーを条件として指定するメソッド
    // alwaysPresentはエンティティが存在しなければ例外を投げる

    // memberIdを9999にしたらEntityAlreadyDeletedExceptionが出た
    // アドバイスやクエリーの不備、投げられたクエリがメッセージとして出ていた。わかりやすい。
    public void test_会員IDが1の会員を検索できること() throws Exception {
        // ## Arrange ##

        // ## Act ##
        // ## Assert ##
        memberBhv.selectEntity(cb -> cb.acceptPK(1)).alwaysPresent(member -> {
            Integer memberId = member.getMemberId();
            log("memberId: {}", memberId);
            assertEquals(1, memberId);
        });
    }
    
    public void test_生年月日がない会員を検索できること() throws Exception {
        // ## Arrange ##

        // ## Act ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.query().setBirthdate_IsNull();
            cb.query().addOrderBy_UpdateDatetime_Desc();
        });

        // ## Assert ##
        assertHasAnyElement(memberList);
        memberList.forEach(member -> {
            log("memberName: {}, birthdate: {}", member.getMemberName(), member.getBirthdate());
            assertNull(member.getBirthdate());
        });
    }
}
