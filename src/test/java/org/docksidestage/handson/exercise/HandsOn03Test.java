package org.docksidestage.handson.exercise;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.annotation.Resource;

import org.dbflute.cbean.result.ListResultBean;
import org.dbflute.cbean.result.PagingResultBean;
import org.docksidestage.handson.dbflute.exbhv.MemberBhv;
import org.docksidestage.handson.dbflute.exbhv.MemberSecurityBhv;
import org.docksidestage.handson.dbflute.exbhv.PurchaseBhv;
import org.docksidestage.handson.dbflute.exentity.Member;
import org.docksidestage.handson.dbflute.exentity.Purchase;
import org.docksidestage.handson.unit.UnitContainerTestCase;

/**
 * @author r.iwata
 */
public class HandsOn03Test extends UnitContainerTestCase {

    @Resource
    private MemberBhv memberBhv;
    @Resource
    private PurchaseBhv purchaseBhv;
    @Resource
    private MemberSecurityBhv memberSecurityBhv;

    // ===================================================================================
    //                                                                              Silver
    //                                                                              ======
    // [1] 会員名称がSで始まる1968年1月1日以前に生まれた会員を検索
    // memo
    // cb.setupSelect_MemberStatus()はMemberStatusテーブルをjoin
    public void test_会員名称がSで始まる1968年1月1日以前に生まれた会員を検索() throws Exception {
        // ## Arrange ##
        LocalDate borderDate = LocalDate.of(1968, 1, 1);

        // ## Act ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.setupSelect_MemberStatus();
            cb.query().setMemberName_LikeSearch("S", op -> op.likePrefix());
            cb.query().setBirthdate_LessEqual(borderDate);
            cb.query().addOrderBy_Birthdate_Asc();
        });

        // ## Assert ##
        assertHasAnyElement(memberList);
        memberList.forEach(member -> {
            // done iwata map()も悪くないけど、ここも「なかったら落ちて良い場面」なので... by jflute (2026/05/15)
            // get(), orElseThrow(引数なし) でもいいかなと。すぐ後で member.getMemberStatus().isPresent() してるし。
            // へたに orElse(null) とかやると、読み手が「あれ？ないことあるのかな？」って勘繰ってしまう。
            // ここでget()しちゃえば、member.getMemberStatus().isPresent()のアサートの代わりになる。
            // done iwata getMemberName(), getBirthdate() いっぱい呼んでるので、変数に抽出してみましょう by jflute (2026/05/15)
            // assertのところ、文字が込み入ってるので、スッキリさせたい。assertのところこそレビューワーが読むところ。
            String memberName = member.getMemberName();
            LocalDate birthdate = member.getBirthdate();
            String statusName = member.getMemberStatus().get().getMemberStatusName();
            log("memberName: {}, birthdate: {}, statusName: {}", memberName, birthdate, statusName);
            assertTrue(memberName.startsWith("S"));
            assertFalse(birthdate.isAfter(borderDate));
        });
    }

    // [2] 会員ステータスと会員セキュリティ情報も取得して会員を検索
    public void test_会員ステータスと会員セキュリティ情報も取得して会員を検索() throws Exception {
        // ## Arrange ##

        // ## Act ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.setupSelect_MemberStatus();
            cb.setupSelect_MemberSecurityAsOne();
            cb.query().addOrderBy_Birthdate_Asc();
            cb.query().addOrderBy_MemberId_Asc(); // 生年月日がnullの場合はMemberId昇順
        });

        // ## Assert ##
        assertHasAnyElement(memberList);
        memberList.forEach(member -> {
            log("member: {}, status: {}, security: {}",
                    member.getMemberName(),
                    member.getMemberStatus().map(s -> s.getMemberStatusName()).orElse(null),
                    member.getMemberSecurityAsOne().map(s -> s.getReminderQuestion()).orElse(null));
            // done jflute 1on1にてカージナリティのお話 (2026/05/15)
            // カージナリティとは？日本語としては「多重度」
            // DBにおいてカージナリティという言葉使う場面が二つ:
            //
            // o カラムのカージナリティ // カラムのデータの種類数みたいなもの
            //   (indexのお話、btreeのお話)
            //
            // o テーブル間のカージナリティ
            //
            // ここでは、テーブル間のカージナリティのお話。
            //
            // 第一段階: (数)
            // 会員1人につき、購入は？ → 多 (逆から見たら会員は1)
            // 会員1人につき、ステータスは？ → 1 (逆から見たら会員は多)
            // 会員1人につき、セキュリティは？ → 1 (逆から見ても会員は1)
            // one-to-many, many-to-one, one-to-one という関係性。
            // 1:n とか 1:多 とか色々な表現方法があるけど同じ。
            //
            // 第二段階: (必須)
            // 会員1人につき、セキュリティは必ずあるか？ → ありそう？あると言い切れる？
            // 会員1人につき、退会情報は必ずあるか？ → ないこともある
            //
            // 1:1 ではなく、1 : 0..1 / 1 : 1 
            // 1:n ではなく、1 : 0..n / 1 : 1..n 
            // 
            // 会員から見て会員ステータスは、必ず存在するものでしょうか？その保証は？
            //  → NotNull制約があるので、少なくと null で存在しないってことはない
            //  → FK制約(外部キー制約)があるので、デタラメなコードで存在しないってことはない
            //  → だから、(探しにさえ行けば)必ず存在すると言い切れる
            //  → 物理的に保証されている: NotNullのFKだから
            //
            // 会員から見て会員セキュリティは、必ず存在するものでしょうか？その保証は？
            //  → 探しに行く方向と、FKの方向が逆、DB制約的には参照されてる保証はない
            //  → リレーションシップ線の黒丸、ないかもしれないの表現
            //  → ↑これはERD上のドキュメント表現なので、実際にDBMSに何か情報として作用するわけじゃない
            //  → つまり、物理的な制約ではなく、論理的な制約(業務的な制約)、つまり人間の決め事
            //  → もういっこ、テーブルコメントに「会員一人につき必ず一つのセキュリティ情報がある」って書いてある
            //
            // ※一方で、ERDのカージナリティ表現がただしくされてるか？というのは現場の別問題（＞＜。
            //
            assertTrue(member.getMemberStatus().isPresent());
            assertTrue(member.getMemberSecurityAsOne().isPresent());
        });
        // #1on1: SchemaHTML自体のお勉強 (2026/05/22)
    }

    // [3] 会員セキュリティ情報のリマインダ質問で「2」を含む会員を検索
    // memo
    // AsOneは1対1の関連のときは1件しかないことがわかっているので指定
    // Actでは操作、Assertでは結果が期待通りであることを守備範囲とする
    // setupSelect_MemberSecurityAsOneを書くとSELECTにセキュリティ情報のテーブルがのってしまう
    public void test_会員セキュリティ情報のリマインダ質問で2を含む会員を検索() throws Exception {
        // ## Arrange ##

        // ## Act ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            // セキュリティ情報自体は取得しないので setupSelect しない
            cb.query().queryMemberSecurityAsOne().setReminderQuestion_LikeSearch("2", op -> op.likeContain());
        });

        // ## Assert ##
        assertHasAnyElement(memberList);
        // #1on1: 本来だったら、n+1問題対応してもらうとかあるところ (2026/05/22)
        // n+1 の n は、memberListの件数(20件として)。20+1 = 21 回のSQLが発行されることになる。
        // 一気に取るのと、分けて取るので、(業務的な)データの総量は、そんなに変わらない。
        // でも、SQL文字列、その文字列を解釈する処理コスト、データ通信上の事務的なコストなどなど、
        // そういうのが掛け算になって、地味に遅くなる。配送料みたいなもの。
        memberList.forEach(member -> {
            // 検証用に別途取得（取得対象外のため）
            // done iwata 基点テーブルがMEMBERである必要がないような？MEMBER_SECURITYを基点にして検索でも良いのでは？ by jflute (2026/05/15)
            String reminder = memberSecurityBhv.selectEntity(cb -> {
                cb.query().setMemberId_Equal(member.getMemberId());
            }).get().getReminderQuestion();
            log("member: {}, reminder: {}", member.getMemberName(), reminder);

            assertTrue(reminder.contains("2"));
        });
    }

    // ===================================================================================
    //                                                                                Gold
    //                                                                                ====
    // [4] 会員ステータスの表示順カラムで会員を並べて検索
    public void test_会員ステータスの表示順カラムで会員を並べて検索() throws Exception {
        // ## Arrange ##

        // ## Act ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            // ステータスのデータ自体は取得しない（Selectしない）→ orderByだけだとINNER JOIN追加される
            cb.query().queryMemberStatus().addOrderBy_DisplayOrder_Asc();
            cb.query().addOrderBy_MemberId_Desc();
        });

        // ## Assert ##
        assertHasAnyElement(memberList);
        // ステータスデータが取れていないこと（OptionalEntityがempty）
        memberList.forEach(member -> {
            assertFalse(member.getMemberStatus().isPresent());
        });
        // ステータスごとに固まって並んでいることを確認
        // → ステータスコードが切り替わったら、それ以前のコードが再登場しないことをチェック
        String prevStatusCode = null;
        java.util.Set<String> seenCodes = new java.util.HashSet<>();
        for (Member member : memberList) {
            String code = member.getMemberStatusCode();
            log("memberId: {}, statusCode: {}", member.getMemberId(), code);
            if (!code.equals(prevStatusCode)) {
                assertFalse(seenCodes.contains(code));
                // done iwata add()とpreの設定、ifの外でも良いのでは？読み手の負担軽減のために by jflute (2026/05/15)
                // ifの中に入ってると、その変数のライフサイクルに分岐があるので、頭の中でちょっと考える。
                // seenCodesは重複がないsetなので、とにかく毎回突っ込んでseenのcodeたちってニュアンス。
                // prevStatusCodeはprevの意味が少し変わって、必ず一個前のstatusってニュアンス。
            }
            seenCodes.add(code);
            prevStatusCode = code;
        }
    }

    // [5] 生年月日が存在する会員の購入を検索
    // memo
    // .withXXX()は「取った先のテーブルから、さらに別のテーブルも取る」というネスト指定
    //
    public void test_生年月日が存在する会員の購入を検索() throws Exception {
        // ## Arrange ##

        // ## Act ##
        // #1on1: 基点テーブルバッチリGood (2026/05/15)
        // 些細なことだけど、もっと複雑な検索とかになると、基点テーブルがわかりにくくなる。
        // そのとき、基点テーブルを間違えると、ギャップでどんどん苦しくなる。
        ListResultBean<Purchase> purchaseList = purchaseBhv.selectList(cb -> {
            cb.setupSelect_Member().withMemberStatus(); // 「Purchase → Member」と「Member → MemberStatus」の2段取る
            cb.setupSelect_Product(); //「Purchase → Product」だけ取る
            cb.query().queryMember().setBirthdate_IsNotNull();
            cb.query().addOrderBy_PurchaseDatetime_Desc();
            cb.query().addOrderBy_PurchasePrice_Desc();
            cb.query().addOrderBy_ProductId_Asc();
            cb.query().addOrderBy_MemberId_Asc();
        });

        // ## Assert ##
        assertHasAnyElement(purchaseList);
        purchaseList.forEach(purchase -> {
            Member member = purchase.getMember().get();
            log("memberName: {}, statusName: {}, productName: {}, birthdate: {}",
                    member.getMemberName(),
                    member.getMemberStatus().map(s -> s.getMemberStatusName()).orElse(null),
                    purchase.getProduct().map(p -> p.getProductName()).orElse(null),
                    member.getBirthdate());
            assertNotNull(member.getBirthdate());
        });
    }

    // [6] 2005年10月1日から3日までに正式会員になった会員を検索
    // memo
    // atStartOfDayは0:00にするもの
    // setFormalizedDatetime_FromToの引数の型がLocalDateTimeなので時分秒を追加
    public void test_2005年10月1日から3日までに正式会員になった会員を検索() throws Exception {
        // ## Arrange ##
        // #1on1: 10/3を含むか？ (2026/05/15)
        // 一方で、来週の水曜まで休みます、は何曜日に出社？
        // 自然言語の日付表現って曖昧なもの。なので常に意識して、確認をする習慣を。
        LocalDate fromDate = LocalDate.of(2005, 10, 1);
        LocalDate toDate = LocalDate.of(2005, 10, 3);

        // ## Act ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.setupSelect_MemberStatus();
            // 取得カラムを絞る：会員ステータス名称のみ（コードはPKで自動含有）
            // #1on1: compareAsDate() のお話 (2026/05/15)
            cb.specify().specifyMemberStatus().columnMemberStatusName();
            cb.query().setFormalizedDatetime_FromTo(fromDate.atStartOfDay(), toDate.atStartOfDay(), op -> op.compareAsDate());
            cb.query().setMemberName_LikeSearch("vi", op -> op.likeContain());
        });

        // ## Assert ##
        assertHasAnyElement(memberList);
        memberList.forEach(member -> {
            log("member: {}, formalized: {}, statusName: {}",
                    member.getMemberName(),
                    member.getFormalizedDatetime(),
                    member.getMemberStatus().map(s -> s.getMemberStatusName()).orElse(null));
            assertNotNull(member.getFormalizedDatetime());

            LocalDate formalizedDate = member.getFormalizedDatetime().toLocalDate();
            assertFalse(formalizedDate.isBefore(fromDate));
            assertFalse(formalizedDate.isAfter(toDate));

            // ステータスはコードと名称のみ取得されている
            assertTrue(member.getMemberStatus().isPresent());
            assertNotNull(member.getMemberStatus().get().getMemberStatusName());
        });
    }

    // ===================================================================================
    //                                                                            Platinum
    //                                                                            ========
    // [7] 正式会員になってから一週間以内の購入を検索
    // memo
    // fd:FormalizedDatetime（正式会員日時）
    // pd:PurchaseDatetime（購入日時）
    public void test_正式会員になってから一週間以内の購入を検索() throws Exception {
        // ## Arrange ##

        // ## Act ##
        // #1on1: このコメントGood, ColumnQueryを知った上でこうしてるってのが伝わる (2026/05/15)
        // ColumnQueryは数値列前提で日付加算をDB側に投げられないため、
        // 「購入日時 ∈ [正式会員日時, 正式会員日時+7日]」はJavaで判定する。
        // done iwata 一方で、ColumnQueryで日付加算もできるので、チャレンジしてみましょう by jflute (2026/05/15)
        ListResultBean<Purchase> purchaseList = purchaseBhv.selectList(cb -> {
            cb.setupSelect_Member().withMemberStatus();
            cb.setupSelect_Member().withMemberSecurityAsOne();
            cb.setupSelect_Product().withProductStatus();
            cb.setupSelect_Product().withProductCategory().withProductCategorySelf();
            cb.query().queryMember().setFormalizedDatetime_IsNotNull();
            cb.columnQuery(c -> c.specify().columnPurchaseDatetime())
                .greaterEqual(c -> c.specify().specifyMember().columnFormalizedDatetime());
            cb.columnQuery(c -> c.specify().columnPurchaseDatetime())
                .lessEqual(c -> c.specify().specifyMember().columnFormalizedDatetime())
                .convert(op -> op.addDay(7));
        });

        // done iwata これはこれで思い出として、コメントアウトとかで残しておきましょう by jflute (2026/05/15)
//        List<Purchase> purchaseList = rawList.stream()
//                .filter(p -> {
//                    LocalDateTime fd = p.getMember().get().getFormalizedDatetime();
//                    LocalDateTime pd = p.getPurchaseDatetime();
//                    return !pd.isBefore(fd) && !pd.isAfter(fd.plusDays(7));
//                })
//                .collect(Collectors.toList());

        // ## Assert ##
        assertHasAnyElement(purchaseList);
        purchaseList.forEach(purchase -> {
            Member member = purchase.getMember().get();
            String parentCategoryName = purchase.getProduct()
                    .flatMap(p -> p.getProductCategory())
                    .flatMap(c -> c.getProductCategorySelf())
                    .map(pc -> pc.getProductCategoryName())
                    .orElse(null);
            // done iwata getPurchaseDatetime()/getFormalizedDatetime()ノイズが多いので、変数にして欲しい by jflute (2026/05/15)
            // ロジカルな行に事務的な処理を含めたくない。ロジカルな行こそロジックに集中して読みたい。
            // こういう配慮がレビューしやすいコードにつながる。
            LocalDateTime purchaseDatetime = purchase.getPurchaseDatetime();
            LocalDateTime formalizedDatetime = member.getFormalizedDatetime();
            log("purchaseDatetime: {}, formalized: {}, parentCategory: {}", purchaseDatetime, formalizedDatetime, parentCategoryName);
            assertNotNull(parentCategoryName);
            // 購入日時が正式会員日時から1週間以内
            assertFalse(purchaseDatetime.isBefore(formalizedDatetime));
            assertFalse(purchaseDatetime.isAfter(formalizedDatetime.plusDays(7)));
        });
    }

    // [8] 1974年までに生まれた、もしくは不明の会員を検索
    public void test_1974年までに生まれたもしくは不明の会員を検索() throws Exception {
        // ## Arrange ##
        // 画面から "1974/01/01" がリクエストされた想定。日付移動はせずそのまま使う
        String birthdateStr = "1974/01/01";
        LocalDate borderDate = LocalDate.parse(birthdateStr, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        // きわどいデータを作成（1974/12/31は含まれる想定、1975/01/01は含まれない想定）
        adjustMember_Birthdate();

        // ## Act ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.setupSelect_MemberStatus();
            cb.setupSelect_MemberSecurityAsOne();
            cb.setupSelect_MemberWithdrawalAsOne();
            cb.specify().specifyMemberStatus().columnMemberStatusName();
            cb.specify().specifyMemberSecurityAsOne().columnReminderQuestion();
            cb.specify().specifyMemberSecurityAsOne().columnReminderAnswer();
            cb.specify().specifyMemberWithdrawalAsOne().columnWithdrawalReasonInputText();
            // done iwata orScopeQuery()も悪くないけど、FromToOptionだけで or IsNull も表現できます by jflute (2026/05/15)
            cb.query().setBirthdate_FromTo(null, borderDate, op ->
                op.compareAsYear().allowOneSide().orIsNull()
            );
            cb.query().addOrderBy_Birthdate_Asc().withNullsFirst();
        });

        // ## Assert ##
        assertHasAnyElement(memberList);
        // 生まれが不明の会員が先頭、若い順で並ぶ
        boolean nullSeen = false;
        boolean nonNullSeen = false;
        for (Member member : memberList) {
            log("name: {}, birthdate: {}, status: {}, reminderQ: {}, reminderA: {}, withdrawalText: {}",
                    member.getMemberName(),
                    Optional.ofNullable(member.getBirthdate()).map(d -> d.toString()).orElse("none"),
                    member.getMemberStatus().map(s -> s.getMemberStatusName()).orElse("none"),
                    member.getMemberSecurityAsOne().map(s -> s.getReminderQuestion()).orElse("none"),
                    member.getMemberSecurityAsOne().map(s -> s.getReminderAnswer()).orElse("none"),
                    member.getMemberWithdrawalAsOne().map(w -> w.getWithdrawalReasonInputText()).orElse("none"));
            LocalDate birthdate = member.getBirthdate();
            // #1on1: if文を使わず||でifの論理性を表現してるの、これはこれでOK (2026/05/22)
            assertTrue(birthdate == null || !birthdate.isAfter(LocalDate.of(1974, 12, 31)));
            nullSeen = nullSeen || birthdate == null;
            nonNullSeen = nonNullSeen || birthdate != null;
            assertFalse(birthdate == null && nonNullSeen);
        }
        assertTrue(nullSeen); // 不明の会員が存在し、先頭に並んでいる
        // きわどいデータの確認: 1974/12/31の会員は含まれる、1975/01/01の会員は含まれない
        assertTrue(memberList.stream().anyMatch(m -> Integer.valueOf(3).equals(m.getMemberId())));
        assertFalse(memberList.stream().anyMatch(m -> Integer.valueOf(4).equals(m.getMemberId())));
    }

    // [9] 2005年6月に正式会員になった会員を先に並べて生年月日のない会員を検索
    public void test_2005年6月に正式会員になった会員を先に並べて生年月日のない会員を検索() throws Exception {
        // ## Arrange ##
        // 画面から "2005/06/01" がリクエストされた想定。日付移動はせずそのまま使う
        String formalizedStr = "2005/06/01";
        LocalDate formalizedDate = LocalDate.parse(formalizedStr, DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        // ## Act ##
        ListResultBean<Member> memberList = memberBhv.selectList(cb -> {
            cb.query().setBirthdate_IsNull();
            // #1on1: ManualOrderのSQL (2026/06/12)
            // #1on1: えこひいきソート、どんなときに使う？ (2026/06/12)
            // e.g. 最近ログインした人を先に並べるとか、お得意先を先に並べる
            // 現場でのManualOrderを見てみた。
            // #1on1: adminというシステムの存在のお話 (2026/06/12)
            cb.query().addOrderBy_FormalizedDatetime_Asc().withManualOrder(op -> {
                op.when_FromTo(formalizedDate.atStartOfDay(), formalizedDate.atStartOfDay(), f -> f.compareAsMonth());
            });
            cb.query().addOrderBy_MemberId_Desc();
        });

        // ## Assert ##
        // TODO iwata 万が一6月のデータがなかったときに検知できるようにしてみよう by jflute (2026/06/12)
        // TODO iwata 万が一6月のデータしかなかったときに検知できるようにしてみよう by jflute (2026/06/12)
        assertHasAnyElement(memberList);
        boolean nonJune2005Seen = false;
        for (Member member : memberList) {
            log("name: {}, birthdate: {}, formalized: {}",
                    member.getMemberName(),
                    member.getBirthdate(),
                    member.getFormalizedDatetime());
            assertNull(member.getBirthdate()); // 生年月日が存在しない
            LocalDateTime formalizedDatetime = member.getFormalizedDatetime();
            boolean isJune2005 = formalizedDatetime != null
                    && formalizedDatetime.getYear() == 2005
                    && formalizedDatetime.getMonthValue() == 6;
            if (!isJune2005) {
                nonJune2005Seen = true;
            }
            // 2005年6月の会員の前に2025年6月ではない会員が出てはいけない
            assertFalse(isJune2005 && nonJune2005Seen);
        }
    }

    // memo ページング検索とは？のページを読んだ
    // 排他制御の部分なるほど、普段アプリ側のコードを触ることが少ないので解像度が低かった
    // #1on1: 実直さが武器になる (2026/06/12)

    // [10] 全ての会員をページング検索
    // pageNumberは取得対象のページ、pageSizeは1ページあたりの件数、pageRangeは現在ページの前後N件に絞るもの
    public void test_全ての会員をページング検索() throws Exception {
        // ## Arrange ##
        int pageSize = 3;
        int pageNumber = 1;

        // ## Act ##
        // selectPage はカウント検索と実データ検索の2つのSQLを発行する
        // #1on1: ページングの様々な配慮の話 (2026/06/12)
        // さらに、MySQLのfound_rows()の話。ManualThreadDataSourceHandlerの話も。
        PagingResultBean<Member> page = memberBhv.selectPage(cb -> {
            cb.setupSelect_MemberStatus();
            cb.query().addOrderBy_MemberId_Asc();
            cb.paging(pageSize, pageNumber);
        });

        // ## Assert ##
        assertHasAnyElement(page);
        page.forEach(member -> log("memberId: {}, name: {}, status: {}",
                member.getMemberId(),
                member.getMemberName(),
                member.getMemberStatus().get().getMemberStatusName()));
        int allRecordCount = memberBhv.selectCount(cb -> {});
        int expectedAllPageCount = (allRecordCount + pageSize - 1) / pageSize;
        List<Integer> pageNumberList = page.pageRange(op -> op.rangeSize(3)).createPageNumberList();
        log("pageNumberList: {}", pageNumberList);

        assertEquals(allRecordCount, page.getAllRecordCount());
        assertEquals(expectedAllPageCount, page.getAllPageCount());
        assertEquals(pageSize, page.getPageSize());
        assertEquals(pageNumber, page.getCurrentPageNumber());
        assertEquals(pageSize, page.size());
        assertEquals(Arrays.asList(1, 2, 3, 4), pageNumberList);
        assertFalse(page.existsPreviousPage());
        assertTrue(page.existsNextPage());
    }
    
    // #1on1: カーソル検索はいったんスキップで、区分値の方に進んじゃってOK (2026/06/12)
    // 区分値の自動生成まで体験してもらいたい。

    private void adjustMember_Birthdate() {
        Member m1974 = new Member();
        m1974.setMemberId(3);
        m1974.setBirthdate(LocalDate.of(1974, 12, 31));
        memberBhv.updateNonstrict(m1974);

        Member m1975 = new Member();
        m1975.setMemberId(4);
        m1975.setBirthdate(LocalDate.of(1975, 1, 1));
        memberBhv.updateNonstrict(m1975);
    }
}