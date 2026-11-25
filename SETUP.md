# SETUP — من الصفر إلى APK

هذا الملف يفترض أنك لا تريد لمس أي مشروع موجود. كل خطوة هنا تُنشئ شيئاً جديداً
خاصاً بهذا التطبيق وحده.

---

## 1. مشروع Supabase جديد (منفصل تماماً)

> **مهم:** لا تشغّل `schema.sql` في مشروع قائم. أنشئ مشروعاً جديداً.

1. افتح <https://supabase.com/dashboard> واضغط **New project**.
2. الاسم: `sntp-market` · المنطقة: **West EU (Ireland)** — أقرب منطقة متاحة للجزائر
   من قائمة Free.
3. بعد الجاهزية: **SQL Editor → New query** → الصق كامل `supabase/schema.sql` → **Run**.
4. تحقّق:
   ```sql
   select count(*) from public.markets;        -- 1
   select * from public.market_state;          -- صف واحد للحالة
   select * from public.market_schedule order by day;  -- 7 صفوف
   select count(*) from public.shops;          -- 7
   ```
5. **Project Settings → API** وانسخ:
   - `Project URL` → `SUPABASE_URL`
   - `anon public` → `SUPABASE_ANON_KEY`

`service_role` **لا يوضع في التطبيق أبداً**. يبقى للسيرفر فقط.

---

## 2. مفاتيح GitHub Actions

Repo → **Settings → Secrets and variables → Actions → New repository secret**:

| الاسم | القيمة |
|---|---|
| `SUPABASE_URL` | `https://xxxx.supabase.co` |
| `SUPABASE_ANON_KEY` | مفتاح anon |

بدون هذه المفاتيح يبقى البناء أخضر والتطبيق يعمل من الجدول المحلي مع لافتة
«غير مؤكد» — وهو سلوك مقصود، لا خطأ.

---

## 3. البناء

كل push إلى `main` يبني APK. لا شيء يُبنى على جهازك.

```powershell
git push
gh run watch
gh run download -n sntp-debug-apk -D dist
adb install -r dist\app-debug.apk
```

APK الناتج: `app-debug.apk` (حزمة `dz.sntp.market.debug`).

---

## 4. التوقيع للإصدار

```powershell
keytool -genkeypair -v -keystore sntp-release.jks -alias sntp `
  -keyalg RSA -keysize 4096 -validity 10000
```

`keystore.properties` في جذر المشروع (مستبعد من git):

```properties
storeFile=sntp-release.jks
storePassword=...
keyAlias=sntp
keyPassword=...
```

**احتفظ بـ `sntp-release.jks` خارج الريبو وفي مكانين.** فقدانه يعني عدم القدرة على
تحديث التطبيق على Play إلى الأبد.

---

## 5. المتجر والاشتراكات

### منتجات Play Console

Play Console → **Monetize with Play → Products → Subscriptions**. المعرّفات يجب
أن تطابق حرفياً:

| SKU | النوع | السعر |
|---|---|---|
| `pro_monthly` | اشتراك شهري | 1000 دج |
| `pro_yearly` | اشتراك سنوي | 9000 دج |
| `boutique_monthly` | — | **لا تُنشئه الآن** |

`boutique_monthly` موجود في الكود (`BillingManager.SKUS`) لكنه **غير معروض في شاشة
الترقية**: «صفحة موثّقة» و«مفتوح الآن» غير منفّذتين، فلا يجوز بيعها بسعر 2500 دج.
أنشئه في Play Console فقط بعد بناء الميزتين، ثم أعد `PlanRow` إلى `UpgradeSheet`.

حتى تُنشأ المنتجات وتُفعّل، يظهر في شاشة الترقية «الدفع غير مُفعّل» ويبقى الزر
معطلاً — لا فشل في منتصف العملية. اختبر الشراء بحساب **License testing** فقط.

### الرعاة (بدل شبكة إعلانات)

```sql
insert into public.sponsors (slot, name, phone, url, ends_at) values
  (1, 'اسم الراعي', '+213...', null, now() + interval '30 days'),  -- تبويب المحلات
  (2, 'اسم الراعي', '+213...', null, now() + interval '30 days'),  -- تبويب السجل
  (3, 'اسم الراعي', '+213...', null, now() + interval '30 days');  -- بطاقة فتح الرقم
```

كل صف منتهي الصلاحية يختفي تلقائياً بفضل RLS. عند غياب الصف يظهر مكان فارغ
مكتوب عليه «مساحة إعلانية»، لا فراغ.

### قبل تحصيل أي مبلغ حقيقي

التحقق الحالي يجري على الجهاز: Play يؤكد الشراء، والتطبيق يمنح الاستحقاق. هذا
لا يحمي من إعادة استخدام توكن مسروق. قبل أول دينار:

1. Edge Function في Supabase تتحقق من `purchaseToken` عبر
   **Android Publisher API** بحساب خدمة، وتكتب في `purchases` و`entitlements`.
2. RTDN عبر Pub/Sub لتحديث التجديد والإلغاء.
3. حتى ذلك الحين أبقِ المنتجات **غير مفعّلة** أو امنح Pro مجاناً.

---

## 7. صفحة الخصوصية (مطلوبة في Play)

الصفحة جاهزة في `docs/privacy.html` (عربي + فرنسي). لتصبح رابطاً عاماً:

1. Repo → **Settings → Pages**.
2. Source: **Deploy from a branch** → Branch: `main` → Folder: `/docs` → Save.
3. بعد دقيقة يصبح الرابط:
   `https://toufikben.github.io/sntp-market/privacy.html`
4. نفس الرابط مسجّل داخل التطبيق (الإعدادات → سياسة الخصوصية). إن غيّرت اسم
   المستودع أو المستخدم، حدّث `PRIVACY_URL` في `MainScreen.kt`.

في Play Console → **App content → Privacy policy** ضع الرابط نفسه. وفي
**Data safety** أجب: الموقع (تقريبي/دقيق) يُجمع عند التصويت فقط، ويُشارَك مع
مزوّد الاستضافة، ويُحذف بعد 30 يوماً؛ معرّفات الجهاز تُجمع؛ لا بيانات مالية
تُجمع مباشرة (Play يتولى الدفع).

---

## 6. قبل الإطلاق

- [ ] `schema.sql` يعمل على مشروع جديد ويُرجع البيانات المتوقعة
- [ ] `SUPABASE_URL` و`SUPABASE_ANON_KEY` مضبوطان في GitHub Secrets
- [ ] التطبيق يفتح ويظهر «السوق مغلق — يفتح 07:00» مع لافتة «غير مؤكد»
- [ ] طلب إذن الموقع يظهر عند أول تصويت
- [ ] إشعار الصباح يصل على جهاز حقيقي (وليس محاكياً)
- [ ] الوضع الليلي: تلقائي/فاتح/داكن يعمل ولا يقلب ألوان الحالة
- [ ] `claim_promo` يمنح Pro مرة واحدة للجهاز ولا يتجاوز 500
- [ ] منتجات Play الثلاثة منشأة (أو متروكة معطّلة عمداً)
- [ ] صفوف `sponsors` أو تركها فارغة — تظهر «مساحة إعلانية»
- [ ] Play Console: Data Safety · سياسة خصوصية منشورة برابط · لقطات شاشة
- [ ] 12 مختبِراً × 14 يوماً (حساب شخصي)
