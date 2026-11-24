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

## 5. قبل الإطلاق

- [ ] `schema.sql` يعمل على مشروع جديد ويُرجع البيانات المتوقعة
- [ ] `SUPABASE_URL` و`SUPABASE_ANON_KEY` مضبوطان في GitHub Secrets
- [ ] التطبيق يفتح ويظهر «السوق مغلق — يفتح 07:00» مع لافتة «غير مؤكد»
- [ ] طلب إذن الموقع يظهر عند أول تصويت
- [ ] إشعار الصباح يصل على جهاز حقيقي (وليس محاكياً)
- [ ] Play Console: Data Safety · سياسة خصوصية · لقطات شاشة
- [ ] 12 مختبِراً × 14 يوماً (حساب شخصي)
