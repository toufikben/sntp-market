-- =============================================================================
--  SNTP — سوق سانتيبي · مخطط قاعدة البيانات
--  Supabase / Postgres 15+
--  شغّل هذا الملف مرة واحدة في SQL Editor.
-- =============================================================================

create extension if not exists "pgcrypto";
create extension if not exists "pg_cron";
-- Needed for the sponsors exclusion constraint below: an equality test on `slot`
-- (an int) has no gist operator class without this extension.
create extension if not exists "btree_gist";

-- -----------------------------------------------------------------------------
-- 1. الأسواق
-- -----------------------------------------------------------------------------
create table if not exists public.markets (
    id            text primary key,
    slug          text unique not null,
    name_ar       text not null,
    name_fr       text not null,
    area_ar       text,
    area_fr       text,
    lat           double precision not null,
    lng           double precision not null,
    timezone      text not null default 'Africa/Algiers',
    vote_radius_m double precision not null default 1200,
    far_radius_m  double precision not null default 5000,
    active        boolean not null default true,
    created_at    timestamptz not null default now()
);

insert into public.markets (id, slug, name_ar, name_fr, area_ar, area_fr, lat, lng)
values (
    'sntp-el-hamiz',
    'sntp-el-hamiz',
    'سوق سانتيبي SNTP',
    'Marché SNTP',
    'الحميز · الدار البيضاء · الجزائر العاصمة',
    'El Hamiz · Dar El Beïda · Alger',
    36.724663,
    3.222052
)
on conflict (id) do nothing;

-- -----------------------------------------------------------------------------
-- 2. الجدول الأسبوعي المعتاد
-- -----------------------------------------------------------------------------
create table if not exists public.market_schedule (
    market_slug text not null references public.markets(slug) on delete cascade,
    day         int  not null check (day between 1 and 7),   -- 1 = الاثنين … 7 = الأحد
    open        text,                                        -- 'HH24:MI' أو null = مغلق
    close       text,
    note_ar     text,
    note_fr     text,
    primary key (market_slug, day)
);

insert into public.market_schedule (market_slug, day, open, close, note_ar, note_fr) values
    ('sntp-el-hamiz', 1, null,  null,  'معتاد الإغلاق — بعض المحلات تفتح صباحاً', 'Fermeture habituelle — certains ouvrent le matin'),
    ('sntp-el-hamiz', 2, '07:00', '13:00', null, null),
    ('sntp-el-hamiz', 3, '06:00', '17:00', 'أكثر الأيام ازدحاماً', 'Jour le plus chargé'),
    ('sntp-el-hamiz', 4, '07:00', '12:00', 'التوقيت يتغيّر كثيراً', 'Horaire très variable'),
    ('sntp-el-hamiz', 5, null,  null,  'إغلاق شبه كامل', 'Fermeture quasi totale'),
    ('sntp-el-hamiz', 6, '06:00', '13:00', null, null),
    ('sntp-el-hamiz', 7, '06:00', '13:00', null, null)
on conflict (market_slug, day) do nothing;

-- -----------------------------------------------------------------------------
-- 3. المحلات
-- -----------------------------------------------------------------------------
create table if not exists public.shops (
    id             text primary key,
    market_slug    text not null references public.markets(slug) on delete cascade,
    name           text not null,
    category       text,
    phone          text,
    whatsapp       text,
    shop_number    text,
    lat            double precision,
    lng            double precision,
    is_verified    boolean not null default false,   -- تحقّقنا من المعلومة
    verified_owner boolean not null default false,   -- المالك تبنّى المحل
    owner_device   text,
    claim_code     text,
    claim_verified boolean not null default false,
    active         boolean not null default true,
    created_at     timestamptz not null default now()
);

create index if not exists shops_market_idx on public.shops (market_slug) where active;

-- shop_number stays null: the P6GC+… codes in the public listings are Google
-- Plus Codes (short coordinates), not the numbering the market uses, and two of
-- them (P6GC+R, P6GC+Q) are truncated and do not resolve at all. Showing them as
-- a shop number would send a trader to a number no shop answers to.
insert into public.shops (id, market_slug, name, category, phone, whatsapp, shop_number) values
    ('sntp-001', 'sntp-el-hamiz', 'محلات SNTP للملابس الرجالية',            'ملابس رجالية', '+213658980632', '+213658980632', null),
    ('sntp-002', 'sntp-el-hamiz', 'بيع ملابس بالجملة عند السعيد',           'ملابس بالجملة', null,           null,            null),
    ('sntp-003', 'sntp-el-hamiz', 'SK 13 SNTP',                              'ملابس',        '+213777766681', '+213777766681', null),
    ('sntp-004', 'sntp-el-hamiz', 'Market Dz KK',                            'ملابس',        '+213772644553', '+213772644553', null),
    ('sntp-005', 'sntp-el-hamiz', 'Tamim SNTP',                              'ملابس',        '+213550142552', '+213550142552', null),
    ('sntp-006', 'sntp-el-hamiz', 'Ab Mode SNTP',                            'ملابس رجالية', null,           null,            null),
    ('sntp-007', 'sntp-el-hamiz', 'شلبي للتسوق — أقمصة وهدايا الحج والعمرة', 'أقمصة وهدايا', '+213552508621', '+213552508621', null)
on conflict (id) do nothing;

-- -----------------------------------------------------------------------------
-- 4. الأجهزة والسمعة
-- -----------------------------------------------------------------------------
create table if not exists public.devices (
    device_id     text primary key,
    reputation    int  not null default 100,   -- 100 = عادي، 180 = موثوق جداً، 40 = مشكوك فيه
    votes_total   int  not null default 0,
    votes_agreed  int  not null default 0,     -- وافق الإجماع اللاحق
    votes_dissented int not null default 0,
    blocked       boolean not null default false,
    recovery_hash text,
    created_at    timestamptz not null default now(),
    last_seen     timestamptz not null default now()
);

-- -----------------------------------------------------------------------------
-- 5. التصويتات
-- -----------------------------------------------------------------------------
create table if not exists public.reports (
    id          bigserial primary key,
    market_slug text not null references public.markets(slug) on delete cascade,
    status      text not null check (status in ('open','closed')),
    opens_at    text,
    closes_at   text,
    reason      text,
    device_id   text not null,
    lat         double precision,
    lng         double precision,
    accuracy_m  double precision,
    client_time timestamptz,
    server_time timestamptz not null default now(),
    distance_m  double precision,
    weight      double precision,
    counted     boolean not null default true,
    -- Scored once, ever. Without this the same vote is rewarded again each time
    -- the state flips back (votes age out, the state returns to unknown, a new
    -- confirmation arrives) and a patient trader farms reputation for free.
    scored_at   timestamptz
);

create index if not exists reports_market_time_idx on public.reports (market_slug, server_time desc);
create index if not exists reports_device_idx on public.reports (device_id, server_time desc);

-- Re-running this file must not fail on a project that already ran an older
-- version: `create table if not exists` silently skips the new column, and every
-- function below would then break at runtime instead of here.
alter table public.reports add column if not exists scored_at timestamptz;

-- -----------------------------------------------------------------------------
-- 6. حالة السوق (الإجماع) + تجاوز الإدارة
-- -----------------------------------------------------------------------------
create table if not exists public.market_state (
    market_slug     text primary key references public.markets(slug) on delete cascade,
    status          text not null default 'unknown' check (status in ('open','closed','unknown')),
    opens_at        text,
    closes_at       text,
    reason_ar       text,
    reason_fr       text,
    confidence      double precision not null default 0,
    reporters_count int not null default 0,
    source          text not null default 'seed' check (source in ('admin','traders','seed')),
    -- Nullable on purpose: it means "no vote has ever been counted for this
    -- market". The client reads it to tell «لا بيانات اليوم» from «غير مؤكد», and
    -- a `default now()` stamped a row nobody had reported on as though evidence
    -- had just arrived, so the card claimed the traders had disagreed.
    updated_at      timestamptz
);

insert into public.market_state (market_slug) values ('sntp-el-hamiz')
on conflict (market_slug) do nothing;

-- Every market gets a state row, not only the one that shipped. A market added to
-- `markets` without this insert used to have nowhere for the consensus to land,
-- and the client read an empty set for it forever.
insert into public.market_state (market_slug)
select m.slug from public.markets m
on conflict (market_slug) do nothing;

-- Re-running this file must not fail on a project created by an older version,
-- where the column was `not null default now()`.
alter table public.market_state alter column updated_at drop not null;
alter table public.market_state alter column updated_at drop default;

-- And a project that already ran the old version carries a stamp it never earned.
-- Cleared only where the market has no counted report at all, so a market that was
-- reported earlier today and has since aged out of the consensus keeps its
-- evidence — that stamp is what stops the card from calling a reported day empty.
update public.market_state ms
   set updated_at = null
 where ms.updated_at is not null
   and not exists (
       select 1 from public.reports r
        where r.market_slug = ms.market_slug and r.counted
   );

create table if not exists public.market_override (
    market_slug text primary key references public.markets(slug) on delete cascade,
    status      text not null check (status in ('open','closed')),
    opens_at    text,
    closes_at   text,
    reason_ar   text,
    reason_fr   text,
    expires_at  timestamptz not null,
    set_by      text,
    updated_at  timestamptz not null default now()
);

-- -----------------------------------------------------------------------------
-- 7. سجل الأيام (للتقويم الشهري)
-- -----------------------------------------------------------------------------
create table if not exists public.state_history (
    market_slug text not null references public.markets(slug) on delete cascade,
    day         date not null,
    status      text not null,
    opens_at    text,
    closes_at   text,
    confidence  double precision,
    source      text,
    updated_at  timestamptz not null default now(),
    primary key (market_slug, day)
);

-- -----------------------------------------------------------------------------
-- 8. الاشتراكات والاستحقاقات
-- -----------------------------------------------------------------------------
create table if not exists public.products (
    sku        text primary key,
    tier       text not null check (tier in ('pro','boutique')),
    period     text not null check (period in ('monthly','yearly')),
    price_dzd  int not null,
    active     boolean not null default true
);

insert into public.products (sku, tier, period, price_dzd) values
    ('pro_monthly',     'pro',      'monthly', 1000),
    ('pro_yearly',      'pro',      'yearly',  9000),
    ('boutique_monthly','boutique', 'monthly', 2500)
on conflict (sku) do nothing;

create table if not exists public.purchases (
    id             bigserial primary key,
    device_id      text not null,
    sku            text not null,
    purchase_token text unique not null,
    order_id       text,
    state          text not null,             -- ACTIVE / GRACE / ON_HOLD / PAUSED / EXPIRED
    expires_at     timestamptz,
    raw            jsonb,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now()
);

create table if not exists public.entitlements (
    device_id  text primary key,
    tier       text not null check (tier in ('free','pro','boutique')),
    source     text not null check (source in ('play','promo','admin')),
    expires_at timestamptz,
    updated_at timestamptz not null default now()
);

create table if not exists public.promo_grants (
    id         bigserial primary key,
    device_id  text not null unique,
    tier       text not null default 'pro',
    reason     text,
    granted_at timestamptz not null default now()
);

create table if not exists public.billing_events (
    id             bigserial primary key,
    purchase_token text,
    type           text,
    received_at    timestamptz not null default now(),
    payload        jsonb
);

-- -----------------------------------------------------------------------------
-- 9. الرعاة وسجل الإدارة
-- -----------------------------------------------------------------------------
create table if not exists public.sponsors (
    id         bigserial primary key,
    slot       int not null,
    name       text not null,
    phone      text,
    url        text,
    starts_at  timestamptz not null default now(),
    ends_at    timestamptz not null,
    active     boolean not null default true
);

-- A slot is sold as exclusive, and nothing enforced it. Two live rows in slot 1
-- both pass the read policy, and the app renders whichever comes back first
-- (`firstOrNull { it.slot == 1 }`), so a sponsor who paid for the only placement
-- in that slot is replaced — or shares it — with no warning and no refund. The
-- second row is also invisible to the person who sold it, because nothing in the
-- console flags the overlap.
--
-- Enforced in the database rather than in the admin tooling: the rows are written
-- from the SQL editor, from a script, or by hand, and every one of those paths has
-- to hit the same wall. `[]` makes the range closed at both ends, so a placement
-- that starts the exact second another one ends is still a conflict — the app
-- shows one row per slot, and the boundary second is not a meaningful exception.
--
-- `where (active)` on purpose: cancelling a placement and reselling the same slot
-- for the same dates is the ordinary case, and a constraint that counted the dead
-- row would make the second sale impossible.
-- Idempotent: this file is meant to be run more than once (it uses
-- `create table if not exists` and `drop ... if exists` throughout), and a bare
-- ADD CONSTRAINT would abort the whole script the second time.
do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'sponsors_dates_ordered'
    ) then
        alter table public.sponsors
            add constraint sponsors_dates_ordered check (ends_at > starts_at);
    end if;
end $$;

do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'sponsors_slot_no_overlap'
    ) then
        alter table public.sponsors
            add constraint sponsors_slot_no_overlap
            exclude using gist (
                slot with =,
                tstzrange(starts_at, ends_at, '[]') with &&
            ) where (active);
    end if;
end $$;

create table if not exists public.admin_actions (
    id         bigserial primary key,
    actor      text,
    action     text not null,
    market_slug text,
    payload    jsonb,
    created_at timestamptz not null default now()
);

-- =============================================================================
--  المحرّك: وزن كل تصويت + الإجماع
-- =============================================================================

-- وقت الملاحظة الفعلي لتصويت: ما رآه التاجر، لا متى وصل الخبر.
--
-- التصويتات المكتوبة داخل السوق تصل كلها عند عودة الشبكة، فـ server_time هو
-- وقت الإرسال وقد يكون بعد الملاحظة بساعة أو ساعتين. حد الـ20 دقيقة كان
-- يقارن client_time بالفعل، لكن كل ما بعده — نافذة الثلاث ساعات، تحلّل الحداثة،
-- و updated_at — كان يقارن server_time. النتيجة: تصويت عمره 90 دقيقة يصل متأخراً
-- كان يأخذ وزناً كاملاً ويُوسم كأنه حصل الآن، وهو بالضبط نوع الكذب الذي يمنعه
-- التطبيق. دالة واحدة كي لا تختلف النسخ.
--
-- client_time في المستقبل (ساعة هاتف مظبوطة خطأً) يُقصّ إلى وقت الوصول: لا يمنح
-- أحداً أفضلية. وغيابه يعني أننا لا نعرف إلا وقت الوصول، وهو أفضل ما لدينا.
--
-- القصّ إلى p_server_time وليس إلى now()، وهذا هو الفرق كله. `now()` تُقرأ من
-- جديد عند كل نداء، وobserved_at تُنادى من cron كل خمس دقائق: فتصويت واحد بساعة
-- متقدّمة عشر ساعات كان يُقصّ إلى «الآن» في كل مرة، أي أنه يبقى دائماً داخل نافذة
-- الثلاث ساعات، وبوزن الحداثة الكامل، ويثبّت updated_at على «قبل ثوانٍ» — تصويت
-- واحد يجمّد حالة السوق إلى الأبد ولا يتقادم أبداً. والأسوأ: شرط الحذف بعد ثلاثين
-- يوماً يقارن observed_at نفسها، فإحداثيات ذلك الجهاز لا تُحذف أبداً — وعدٌ في
-- سياسة الخصوصية ينقضه سطر واحد.
--
-- p_server_time ثابت في الصف (ضبطه المُشغّل عند الإدخال)، فالقصّ إليه يعني: «لا
-- يمكن أن تكون الملاحظة أحدث من وصولها»، ثم تتقادم كأي تصويت آخر وتُحذف في موعدها.
create or replace function public.observed_at(
    p_client_time timestamptz,
    p_server_time timestamptz
) returns timestamptz
language sql immutable as $$
    select least(coalesce(p_client_time, p_server_time), p_server_time);
$$;

-- الوزن: القرب × السمعة × الحداثة. صفر خارج نطاق 5 كم.
create or replace function public.report_weight(
    p_distance double precision,
    p_reputation int,
    p_age_min double precision,
    p_vote_radius double precision,
    p_far_radius double precision
) returns double precision
language sql immutable as $$
    select
        (case
            when p_distance is null then 0.0
            when p_distance <= p_vote_radius then 1.0
            when p_distance <= p_far_radius then 0.35
            else 0.0
         end)
        *
        (case
            when p_reputation >= 180 then 1.8
            when p_reputation >= 140 then 1.4
            when p_reputation >= 80  then 1.0
            when p_reputation >= 40  then 0.6
            else 0.4
         end)
        *
        (case
            when p_age_min <= 45 then 1.0
            when p_age_min <= 90 then 0.7
            when p_age_min <= 180 then 0.4
            else 0.0
         end);
$$;

-- المسافة بالأمتار (haversine)
create or replace function public.distance_m(
    lat1 double precision, lng1 double precision,
    lat2 double precision, lng2 double precision
) returns double precision
language sql immutable as $$
    select 2 * 6371000 * asin(sqrt(
        power(sin(radians(lat2 - lat1) / 2), 2) +
        cos(radians(lat1)) * cos(radians(lat2)) *
        power(sin(radians(lng2 - lng1) / 2), 2)
    ));
$$;

-- -----------------------------------------------------------------------------
--  لقطة اليوم
--
--  آخر حساب للسوق ليس بالضرورة ما حدث اليوم. تصويت «مفتوح» الساعة الثامنة
--  تنتهي صلاحيته بعد ثلاث ساعات، فيصير الحساب «غير مؤكد» ظهراً — ولو كتبنا
--  ذلك في السجل لمحونا الحقيقة التي رآها التجار صباحاً. لذلك:
--    · لا يُكتب في السجل إلا ما تأكّد فعلاً (مفتوح/مغلق).
--    · «غير مؤكد» يُكتب فقط إذا لم يكن لليوم أي صف بعد.
--    · تجاوز الإدارة يُسجّل أيضاً، وكان يُهمَل سابقاً لأنه يعود قبل اللقطة.
--
--  تُعرَّف قبل recompute_market_state لأن plpgsql يتحقق من وجود الدوال
--  المستدعاة عند الإنشاء (check_function_bodies).
-- -----------------------------------------------------------------------------
create or replace function public.snapshot_day(
    p_slug   text,
    p_status text,
    p_opens  text,
    p_closes text,
    p_conf   double precision,
    p_source text
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    m public.markets%rowtype;
    d date;
begin
    select * into m from public.markets where slug = p_slug;
    if not found then return; end if;

    d := (now() at time zone m.timezone)::date;

    if p_status in ('open','closed') then
        insert into public.state_history
            (market_slug, day, status, opens_at, closes_at, confidence, source)
        values (p_slug, d, p_status, p_opens, p_closes, round(p_conf::numeric, 3), p_source)
        on conflict (market_slug, day) do update set
            -- The calendar answers one question: did the market operate that day?
            -- A day that opened at 06:00 and shut at 14:00 was overwritten with
            -- 'closed' by the evening recompute, so the history denied the market
            -- had ever opened. 'open' is sticky; the closing time is still kept.
            status     = case
                when excluded.source = 'admin' then excluded.status
                when public.state_history.status = 'open' then 'open'
                else excluded.status
            end,
            opens_at   = coalesce(public.state_history.opens_at, excluded.opens_at),
            -- coalesce on this side too, and it was the side that lost data. The
            -- comment above says the closing time is kept, and the code dropped it:
            -- a later verdict that carried no hour (a "closed" vote normally has
            -- none) wrote null over the closing time an earlier vote had reported,
            -- so the calendar kept the opening hour and forgot the closing one —
            -- half a day, in the archive the trader opens to see how long the
            -- market actually stayed open. A newer hour still wins when it exists;
            -- only the null stops erasing.
            closes_at  = coalesce(excluded.closes_at, public.state_history.closes_at),
            -- Confidence must describe the status stored beside it. Taking the
            -- greatest of the two kept the *previous* status's confidence when the
            -- day flipped (a 0.8 "open" followed by a 0.6 "closed" was recorded as
            -- closed 0.8), so a weak verdict borrowed a strong one's certainty.
            confidence = case
                when public.state_history.status = excluded.status
                    then greatest(public.state_history.confidence, excluded.confidence)
                else excluded.confidence
            end,
            source     = excluded.source,
            updated_at = now();
    else
        insert into public.state_history (market_slug, day, status, confidence, source)
        values (p_slug, d, 'unknown', 0, p_source)
        on conflict (market_slug, day) do nothing;
    end if;
end;
$$;

revoke all on function public.snapshot_day(text, text, text, text, double precision, text) from public;

-- إعادة حساب حالة سوق واحد. تُستدعى من cron ومن الـ trigger.
create or replace function public.recompute_market_state(p_slug text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    m            public.markets%rowtype;
    ov           public.market_override%rowtype;
    v_open_w     double precision := 0;
    v_closed_w   double precision := 0;
    v_total_w    double precision := 0;
    v_devices    int := 0;
    v_conf       double precision := 0;
    v_status     text := 'unknown';
    v_opens      text := null;
    v_closes     text := null;
    v_prev_status text;
    v_evidence   timestamptz;
begin
    select * into m from public.markets where slug = p_slug and active;
    if not found then return; end if;

    -- What the screen says right now. Reputation must only move when this
    -- actually changes, not on every cron pass.
    select status into v_prev_status
      from public.market_state where market_slug = p_slug;
    -- A brand-new market has no row yet; coalesce so the comparison below works.
    v_prev_status := coalesce(v_prev_status, 'unknown');

    -- 1) تجاوز الإدارة يتقدّم دائماً
    select * into ov from public.market_override
     where market_slug = p_slug and expires_at > now();

    if found then
        -- An upsert, not an update. `update ... where market_slug = p_slug` wrote
        -- nothing at all when the row did not exist — a market added to `markets`
        -- without the matching `market_state` insert (any second market, or a
        -- project where that one line was skipped) accepted votes, computed a
        -- consensus, and then discarded it: the client's query returned an empty
        -- set, `fetchState` fell back to the seed, and the traders' reports never
        -- reached the screen. The single shipped market happens to have its row,
        -- which is why this never showed.
        insert into public.market_state as ms (
            market_slug, status, opens_at, closes_at, reason_ar, reason_fr,
            confidence, reporters_count, source, updated_at
        ) values (
            p_slug, ov.status, ov.opens_at, ov.closes_at, ov.reason_ar, ov.reason_fr,
            1.0, 0, 'admin', ov.updated_at
        )
        on conflict (market_slug) do update set
            status = excluded.status,
            opens_at = excluded.opens_at,
            closes_at = excluded.closes_at,
            reason_ar = excluded.reason_ar,
            reason_fr = excluded.reason_fr,
            confidence = excluded.confidence,
            reporters_count = excluded.reporters_count,
            source = excluded.source,
            -- The age of the decision, not of this cron pass. Stamping now() here
            -- made an official closure issued six hours ago look like it had been
            -- confirmed seconds ago, on the one card people trust most.
            updated_at = excluded.updated_at;

        -- An official closure is the most important row in the history; it used
        -- to be lost because this branch returned before writing it.
        perform public.snapshot_day(p_slug, ov.status, ov.opens_at, ov.closes_at, 1.0, 'admin');
        return;
    end if;

    -- 2) التصويتات: أحدث تصويت لكل جهاز داخل آخر 3 ساعات
    --
    --  الوثائق تقول إن الوزن = القرب × السمعة × الحداثة، لكن الحداثة كانت
    --  تُحسب صفراً عند الإدخال ثم تُهمَل هنا، فتصويت عمره ساعتان ونصف كان
    --  يزن مثل تصويت عمره دقيقة. تُطبَّق هنا على وزن التصويت المخزّن.
    with latest as (
        select distinct on (device_id)
            device_id, status, opens_at, closes_at,
            server_time, distance_m, weight,
            public.observed_at(client_time, server_time) as seen_at
        from public.reports
        where market_slug = p_slug
          and counted
          -- The window is on when the trader saw it, not on when the vote
          -- reached us: a queue flushed at noon must not resurrect last night.
          and public.observed_at(client_time, server_time) > now() - interval '3 hours'
        -- Newest by observation. Ordering by arrival let an old queued vote
        -- outrank a newer live one and become "the latest vote from this device".
        order by device_id, seen_at desc
    ), aged as (
        select
            status, opens_at, closes_at,
            coalesce(weight, 0) * (case
                when extract(epoch from (now() - seen_at)) / 60.0 <= 45  then 1.0
                when extract(epoch from (now() - seen_at)) / 60.0 <= 90  then 0.7
                when extract(epoch from (now() - seen_at)) / 60.0 <= 180 then 0.4
                else 0.0
            end) as w
        from latest
    )
    select
        coalesce(sum(case when status = 'open'   then w else 0 end), 0),
        coalesce(sum(case when status = 'closed' then w else 0 end), 0),
        coalesce(sum(w), 0),
        count(*) filter (where w > 0)
    into v_open_w, v_closed_w, v_total_w, v_devices
    from aged;

    if v_total_w > 0 then
        -- الفائز يجب أن يحقق 60% من الوزن
        if v_open_w >= 0.6 * v_total_w then
            v_status := 'open';
            v_conf   := v_open_w / v_total_w;
        elsif v_closed_w >= 0.6 * v_total_w then
            v_status := 'closed';
            v_conf   := v_closed_w / v_total_w;
        else
            v_status := 'unknown';
            v_conf   := greatest(v_open_w, v_closed_w) / v_total_w;
        end if;

        -- لا نعتمد إلا بإجماع 3 أجهزة مختلفة على الأقل
        if v_devices < 3 then
            v_status := 'unknown';
            v_conf   := v_conf * (v_devices::double precision / 3.0);
        end if;

        -- وقت الفتح/الإغلاق من أحدث تصويت في الجهة الفائزة
        if v_status in ('open','closed') then
            -- Per column, and the newest row that actually carries a value — not the
            -- newest row.
            --
            -- The hours must come from the most recently *observed* vote, not the
            -- most recently delivered one: a queued vote from 06:00 arriving at
            -- noon would otherwise overwrite the hours a trader reported at 11:45.
            -- But `limit 1` took that newest row whole, nulls included, and a
            -- "closed" vote normally carries no hour at all: one trader tapping
            -- «مغلق» at 14:05 erased the closing time another trader reported at
            -- 14:00 — the card dropped from «يغلق 14:00» back to a bare «مغلق», and
            -- the calendar lost it too. Each column keeps its own newest non-null
            -- value; a vote that knows only one hour still contributes that one.
            select
                (array_agg(opens_at  order by seen_at desc) filter (where opens_at  is not null))[1],
                (array_agg(closes_at order by seen_at desc) filter (where closes_at is not null))[1]
            into v_opens, v_closes
            from (
                select
                    r.opens_at, r.closes_at,
                    public.observed_at(r.client_time, r.server_time) as seen_at
                from public.reports r
                where r.market_slug = p_slug
                  and r.counted
                  and r.status = v_status
                  and public.observed_at(r.client_time, r.server_time) > now() - interval '3 hours'
            ) h;
        end if;
    end if;

    -- updated_at is the evidence timestamp the screen prints ("آخر تحديث") and the
    -- client uses to decide whether to show the stale warning. Two wrong versions
    -- came before this one: now() on every cron pass made a state built from a
    -- three-hour-old vote look seconds old, and tying it to the moment the status
    -- last changed made a market confirmed again ten minutes ago look stale
    -- because the status had not flipped. The honest value is when the newest
    -- counted vote was *observed*: that is the age of the information on screen.
    -- It used to read server_time, so a vote flushed an hour after the trader
    -- tapped it arrived looking brand new.
    select max(public.observed_at(client_time, server_time)) into v_evidence
      from public.reports
     where market_slug = p_slug
       and counted
       and public.observed_at(client_time, server_time) > now() - interval '3 hours';

    -- Same upsert, same reason: the traders' verdict must survive a market whose
    -- `market_state` row was never created.
    insert into public.market_state as ms (
        market_slug, status, opens_at, closes_at, reason_ar, reason_fr,
        confidence, reporters_count, source, updated_at
    ) values (
        p_slug, v_status, v_opens, v_closes, null, null,
        round(v_conf::numeric, 3), v_devices,
        case when v_devices > 0 then 'traders' else 'seed' end,
        v_evidence
    )
    on conflict (market_slug) do update set
        status          = excluded.status,
        opens_at        = excluded.opens_at,
        closes_at       = excluded.closes_at,
        reason_ar       = excluded.reason_ar,
        reason_fr       = excluded.reason_fr,
        confidence      = excluded.confidence,
        reporters_count = excluded.reporters_count,
        -- Provenance has to describe what is actually behind the row. This used to
        -- be hardcoded 'traders', so a market nobody had voted on since it was
        -- created still claimed its source was the traders' votes: the card printed
        -- "لا بيانات اليوم" and, two lines under it, "المصدر: تصويت التجار" —
        -- attribution for votes that do not exist, on the one screen built to be
        -- trusted at a glance. With no counted devices the answer is the seed.
        source          = excluded.source,
        -- coalesce, not a plain assignment: a row that has evidence keeps it when a
        -- quiet pass finds nothing. Losing it turned a day the traders reported into
        -- «لا بيانات اليوم» the moment the votes aged out of the window, on a day
        -- the calendar one tap away still recorded as open.
        updated_at      = coalesce(excluded.updated_at, ms.updated_at);

    -- A day nobody reported on is not a day the traders disagreed about. This
    -- function runs from cron every five minutes, so with no reports v_status is
    -- 'unknown' and snapshot_day's unknown branch still wrote a row for today:
    -- the calendar then showed "unconfirmed" for days the app had simply never
    -- heard about, and the honest "لا سجل بعد" state became unreachable.
    -- An unknown verdict is only worth recording when someone actually voted.
    if v_status <> 'unknown' or v_total_w > 0 then
        perform public.snapshot_day(p_slug, v_status, v_opens, v_closes, v_conf, 'traders');
    end if;

    -- Reputation: a vote that matched the consensus raises the device; a lone
    -- dissent lowers it. Until now this column kept its default forever while the
    -- app told users their score comes from agreeing with later consensus.
    --
    -- It must move only on a real *change* of state. This function runs every five
    -- minutes from cron and after every insert; rewarding on each run would push
    -- every trader to 180 within an hour and make the weight meaningless.
    -- Scores stay in 40..180: one bad day must not make a trader worthless, and
    -- one good day must not make a stranger authoritative.
    if v_status in ('open','closed') and v_prev_status is distinct from v_status then
        -- One vote per device is judged: the same one the consensus above was built
        -- from. Scoring *every* report was the opposite of what the screen promises,
        -- and it punished the best reporters. A trader who reports "open" at 08:00
        -- and then "closed" at 08:25, because he watched it shut, agreed with the
        -- consensus on his newest observation and was still docked 3 for the one he
        -- had correctly superseded — so diligence cost him score, and the more
        -- often a trader updated his answer the lower his weight fell.
        --
        -- Each report is still scored at most once (scored_at), so a state that
        -- flips back and forth cannot be farmed. The marking and the reward happen
        -- in the same statement, which is what makes that guarantee hold.
        with latest as (
            select distinct on (r.device_id)
                r.id, r.device_id, r.status, r.scored_at,
                -- Observed time, not arrival: a vote queued inside the market and
                -- flushed at noon must not be scored as if it were cast then.
                public.observed_at(r.client_time, r.server_time) as seen_at
            from public.reports r
            where r.market_slug = p_slug
              and r.counted
              and public.observed_at(r.client_time, r.server_time) > now() - interval '3 hours'
            -- `id desc` breaks a tie between two votes observed in the same
            -- second; without it the choice is arbitrary and a device could be
            -- judged on the older of the two.
            order by r.device_id, seen_at desc, r.id desc
        ), judged as (
            update public.reports r
               set scored_at = now()
              from latest l
             where r.id = l.id
               -- The newest observation is the one under judgement, and it is
               -- judged once. Filtering on scored_at *inside* the DISTINCT ON
               -- above would have made an older, already-superseded vote the
               -- "latest" the moment the real one was scored.
               and l.scored_at is null
               -- Only a *fresh* contradiction is judged at all. A trader who
               -- reported "open" at 12:30 was right at the time; when the market
               -- closes at 14:00 his vote is still inside the 3-hour window but
               -- must not be scored as a dissent, or the honest early reporter is
               -- punished by hindsight alone. Left unmarked on purpose: if the
               -- consensus later reaches his answer, that same vote is rewarded
               -- then, which is the outcome he earned.
               and (l.status = v_status or l.seen_at > now() - interval '45 minutes')
            returning l.device_id, l.status, l.seen_at
        )
        update public.devices d set
            votes_agreed    = d.votes_agreed
                              + case when j.status = v_status then 1 else 0 end,
            votes_dissented = d.votes_dissented
                              + case when j.status <> v_status then 1 else 0 end,
            reputation = least(180, greatest(40,
                d.reputation
                + case when j.status = v_status then 4 else -3 end
            ))
        from judged j
        where d.device_id = j.device_id;
    end if;
end;
$$;

create or replace function public.recompute_all_markets()
returns void language plpgsql security definer set search_path = public as $$
declare s text;
begin
    for s in select slug from public.markets where active loop
        perform public.recompute_market_state(s);
    end loop;
end;
$$;

-- =============================================================================
--  الحماية: فحص كل تصويت عند الإدخال
-- =============================================================================
create or replace function public.reports_before_insert()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    m       public.markets%rowtype;
    v_dev   public.devices%rowtype;
    v_dist  double precision;
    v_age   double precision;
    v_last_eff timestamptz;
    v_eff   timestamptz;
begin
    select * into m from public.markets where slug = new.market_slug;
    if not found then
        raise exception 'unknown market';
    end if;

    -- الجهاز
    insert into public.devices (device_id) values (new.device_id)
    on conflict (device_id) do nothing;
    select * into v_dev from public.devices where device_id = new.device_id;

    if v_dev.blocked then
        raise exception 'device blocked';
    end if;

    -- الساعة تُنسخ كما هي إلى market_state.opens_at ثم تُطبع على البطاقة حرفياً،
    -- فهي ليست نصاً حراً: أي عميل (والـ anon key داخل التطبيق) يستطيع أن يرسل
    -- «06:00 — السوق مفتوح، اتصل 0555…» فتُعرض في السطر الذي يقرأه التاجر قبل أن
    -- يخرج من بيته. تُقبل HH24:MI فقط، وnull يعني «لا أعرف». القيمة غير الصالحة
    -- تُصفَّر بدل رفض التصويت كله: الحالة التي رآها التاجر هي المعلومة المهمة،
    -- والساعة التالفة ليست سبباً لإسقاطها.
    if new.opens_at is not null and new.opens_at !~ '^([01][0-9]|2[0-3]):[0-5][0-9]$' then
        new.opens_at := null;
    end if;
    if new.closes_at is not null and new.closes_at !~ '^([01][0-9]|2[0-3]):[0-5][0-9]$' then
        new.closes_at := null;
    end if;

    -- حد أدنى بين تصويتين من نفس الجهاز: 20 دقيقة بين الملاحظتين.
    --
    -- المشكلة: التصويتات المكتوبة داخل السوق بلا شبكة تصل كلها دفعة واحدة عند
    -- عودة الاتصال. لو قارنّا بـ server_time (وقت الإدخال) لرُفض كل ما بعد الأول
    -- بـ 'rate limited' وضاع — والتطبيق كان قد وعد التاجر أنها محفوظة.
    --
    -- لذلك نقارن بوقت الملاحظة (client_time) إن وُجد، وهو ما يعبّر عن الواقع:
    -- تصويتان بفارق 30 دقيقة داخل السوق ليسا تكراراً حتى لو وصلا في نفس الثانية.
    -- ولا نحسب وقتاً من المستقبل: ساعة الهاتف لا تمنح أحداً أفضلية.
    --
    -- النافذة متماثلة (±20 دقيقة): تصويت قديم يعود من الطابور بعد أن صوّت التاجر
    -- مرة أخرى ليس تكراراً، ولو قارنّاه بالأحدث وحده لاعتُبر كذلك وضاع.
    --
    -- الحدّان مفتوحان (>) و(<) لا مغلقان: العميل يسمح بالتصويت عند 20 دقيقة
    -- تماماً، و«between» يشمل الطرفين، فكان التاجر الذي ينتظر عشرين دقيقة بدقة
    -- يُرفض من السيرفر بعد أن سمح له التطبيق — رفض لا يستطيع فهمه ولا تجنّبه.
    v_eff := public.observed_at(new.client_time, now());

    select max(public.observed_at(client_time, server_time)) into v_last_eff
      from public.reports
     where device_id = new.device_id and market_slug = new.market_slug
       and public.observed_at(client_time, server_time)
           > v_eff - interval '20 minutes'
       and public.observed_at(client_time, server_time)
           < v_eff + interval '20 minutes';

    if v_last_eff is not null then
        raise exception 'rate limited';
    end if;

    -- وقّت التصويت: لا نثق بساعة الهاتف
    new.server_time := now();
    if new.client_time is not null then
        v_age := extract(epoch from (now() - new.client_time)) / 60.0;
        if v_age > 180 then
            -- تصويت من طابور قديم: يُحفظ لكن لا يُحتسب
            new.counted := false;
        end if;
        -- الساعة في المستقبل لم تكن تُقصّ بل تُسقط التصويت (counted := false عند
        -- تجاوز 10 دقائق). والساعة التي تسبق الحقيقة بأحد عشر دقيقة ليست هجوماً:
        -- هي هاتف ضُبط يدوياً بلا شبكة تصحيح، أو منطقة زمنية اختارها صاحبها خطأً.
        -- صاحبها يرى «تم إرسال تصويتك» ولا يُحتسب له تصويت أبداً — عطل دائم لا
        -- يستطيع تشخيصه ولا إصلاحه، ويصمت التطبيق عنه.
        --
        -- والقصّ هو الحل الصحيح، لا الإسقاط: observed_at() تقصّ كل وقت مستقبلي
        -- إلى وقت الوصول، فالتصويت يُحتسب بوزن لحظة وصوله — وهو أقصى ما يمكن أن
        -- يدّعيه، ولا يمنح الساعة المتقدّمة أي أفضلية على من صوّت في اللحظة نفسها.
        -- إسقاطه كان يضيع تصويتاً صحيحاً بلا مقابل.
    end if;

    -- المسافة
    --
    -- الوزن يستخدم المسافة *الأسوأ حالة*: مسافة الجهاز + هامش الخطأ الذي أبلغ عنه
    -- نظام الموقع. `accuracy_m` كان يُحفظ ولا يُقرأ أبداً، فتصويت من برج شبكة خطؤه
    -- ثلاثة كيلومترات داخل نطاق 1200 م أخذ وزناً كاملاً كأنه قياس دقيق — وهو
    -- بالضبط ما يمنعه التطبيق: من لا يعرف أين هو لا يعرف هل السوق مفتوح.
    -- المسافة الخام تبقى محفوظة للتشخيص.
    --
    -- وهو رقم يرسله العميل، ومفتاح anon موجود داخل التطبيق: أي طلب مباشر إلى
    -- PostgREST يستطيع أن يرسل accuracy_m = -100000 فيصير (المسافة + الهامش)
    -- سالباً، ومن ثم أقل من نصف قطر التصويت — تصويت من أي مكان في العالم بوزن
    -- كامل. الهامش لا معنى له إلا إذا كان مسافة موجبة، ويُقصّ عند سقف يعادل ما
    -- يقوله نظام الموقع فعلاً (برج شبكة سيئ: بضعة كيلومترات). الحدّ الأعلى يمنع
    -- أيضاً accuracy_m = 1e12 من تحويل كل تصويت إلى صفر بصمت.
    if new.accuracy_m is not null then
        new.accuracy_m := least(greatest(new.accuracy_m, 0), m.far_radius_m);
    end if;

    -- إحداثيات خارج نطاق الكوكب ليست قياساً، هي خطأ في الإرسال. تُحفظ بلا إحداثيات
    -- بدل أن تدخل distance_m بحساب لا معنى له (أو ترمي خطأً يمنع التصويت كله).
    if new.lat is not null and new.lng is not null
       and (new.lat not between -90 and 90 or new.lng not between -180 and 180) then
        new.lat := null;
        new.lng := null;
    end if;

    if new.lat is not null and new.lng is not null then
        v_dist := public.distance_m(new.lat, new.lng, m.lat, m.lng);
        new.distance_m := round(v_dist::numeric, 1);
    else
        new.distance_m := null;
    end if;

    new.weight := public.report_weight(
        case
            when new.distance_m is null then null
            else new.distance_m + coalesce(new.accuracy_m, 0)
        end,
        v_dev.reputation,
        0,
        m.vote_radius_m,
        m.far_radius_m
    );

    -- خارج النطاق: يُحفظ للتشخيص لكن لا يُحتسب
    if coalesce(new.weight, 0) <= 0 then
        new.counted := false;
    end if;

    update public.devices set
        votes_total = votes_total + 1,
        last_seen = now()
    where device_id = new.device_id;

    return new;
end;
$$;

drop trigger if exists reports_before_insert on public.reports;
create trigger reports_before_insert
before insert on public.reports
for each row execute function public.reports_before_insert();

-- بعد كل تصويت: أعِد الحساب فوراً (السوق صغير، والعدد قليل)
create or replace function public.reports_after_insert()
returns trigger language plpgsql security definer set search_path = public as $$
begin
    perform public.recompute_market_state(new.market_slug);
    return new;
end;
$$;

drop trigger if exists reports_after_insert on public.reports;
create trigger reports_after_insert
after insert on public.reports
for each row execute function public.reports_after_insert();

-- =============================================================================
--  الصلاحيات (RLS)
-- =============================================================================
alter table public.markets         enable row level security;
alter table public.market_schedule enable row level security;
alter table public.market_state    enable row level security;
alter table public.state_history   enable row level security;
alter table public.shops           enable row level security;
alter table public.reports         enable row level security;
alter table public.sponsors        enable row level security;
alter table public.products        enable row level security;
alter table public.devices         enable row level security;
alter table public.entitlements    enable row level security;

-- قراءة عامة
drop policy if exists read_markets  on public.markets;
create policy read_markets  on public.markets         for select using (active);

drop policy if exists read_schedule on public.market_schedule;
create policy read_schedule on public.market_schedule for select using (true);

drop policy if exists read_state    on public.market_state;
create policy read_state    on public.market_state    for select using (true);

drop policy if exists read_history  on public.state_history;
create policy read_history  on public.state_history   for select using (true);

drop policy if exists read_shops    on public.shops;
create policy read_shops    on public.shops           for select using (active);

drop policy if exists read_sponsors on public.sponsors;
-- A placement is live only between the dates that were sold. `starts_at` was
-- missing from this check, so a sponsor booked for next month appeared on the
-- screen today: the client pays for one month and gets two, and the row that
-- replaced him is shown alongside his own — on a slot sold as exclusive.
create policy read_sponsors on public.sponsors        for select using (
    active and starts_at <= now() and ends_at > now()
);

drop policy if exists read_products on public.products;
create policy read_products on public.products        for select using (active);

-- التصويت: إدخال فقط، بلا قراءة (حتى لا يستخرج أحدٌ سجلات الآخرين)
drop policy if exists insert_reports on public.reports;
create policy insert_reports on public.reports for insert with check (true);

-- الاستحقاقات: لا تُقرأ كجدول. الدالة أدناه تُرجع صف الجهاز نفسه فقط،
-- حتى لا يستطيع أحدٌ سرد اشتراكات كل الأجهزة بالـ anon key.
drop policy if exists read_own_entitlement on public.entitlements;

create or replace function public.my_entitlement(p_device_id text)
returns table (tier text, source text, expires_at timestamptz)
language sql
security definer
set search_path = public
as $$
    -- A device has one row (device_id is the primary key), so the ordering here
    -- is defensive only. The real protection against a promo overwriting a paid
    -- subscription lives in claim_promo.
    select e.tier, e.source, e.expires_at
    from public.entitlements e
    where e.device_id = p_device_id
      and (e.expires_at is null or e.expires_at > now())
    order by
        case e.source when 'play' then 0 when 'admin' then 1 else 2 end,
        case e.tier when 'boutique' then 0 when 'pro' then 1 else 2 end
    limit 1;
$$;

-- ---------------------------------------------------------------- promo
-- أول 500 مستخدم: Pro مدى الحياة. عدّاد داخل قفل استشاري حتى لا يمنح
-- طلبان متزامنان المقعد 500 و501 معاً.
create or replace function public.claim_promo(p_device_id text)
returns table (tier text, source text, expires_at timestamptz)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_granted int;
    v_source  text;
    v_expires timestamptz;
begin
    perform pg_advisory_xact_lock(500500);

    select e.source, e.expires_at into v_source, v_expires
      from public.entitlements e where e.device_id = p_device_id;

    -- A device that already pays through Play keeps paying. Overwriting it with a
    -- lifetime promo would silently end the subscription relationship: no
    -- renewal, no cancellation handling, no revenue. An *expired* Play row is a
    -- different thing and may be replaced. Neither case consumes a promo seat.
    if v_source = 'play' and (v_expires is null or v_expires > now()) then
        return query
            select e.tier, e.source, e.expires_at
            from public.entitlements e
            where e.device_id = p_device_id
            limit 1;
        return;
    end if;

    select count(*) into v_granted from public.promo_grants;

    if v_granted < 500 then
        insert into public.promo_grants (device_id, tier, reason)
        values (p_device_id, 'pro', 'first-500')
        on conflict (device_id) do nothing;

        insert into public.entitlements (device_id, tier, source, expires_at)
        values (p_device_id, 'pro', 'promo', null)
        on conflict (device_id) do update set
            tier = excluded.tier,
            source = excluded.source,
            expires_at = excluded.expires_at,
            updated_at = now()
        where entitlements.source in ('promo', 'admin')
           or entitlements.expires_at <= now();
    end if;

    return query
        select e.tier, e.source, e.expires_at
        from public.entitlements e
        where e.device_id = p_device_id
          and (e.expires_at is null or e.expires_at > now())
        limit 1;
end;
$$;

revoke all on function public.my_entitlement(text) from public;
revoke all on function public.claim_promo(text) from public;
grant execute on function public.my_entitlement(text) to anon, authenticated;
grant execute on function public.claim_promo(text) to anon, authenticated;

-- The score the settings screen shows. It is computed server-side from votes that
-- matched the consensus, so the client must read it rather than keep its own
-- counter — which is what it did before, leaving every device at 100 forever.
create or replace function public.my_reputation(p_device_id text)
returns table (reputation int, votes_total int, votes_agreed int, votes_dissented int)
language sql
security definer
set search_path = public
as $$
    select d.reputation, d.votes_total, d.votes_agreed, d.votes_dissented
    from public.devices d
    where d.device_id = p_device_id
    limit 1;
$$;

revoke all on function public.my_reputation(text) from public;
grant execute on function public.my_reputation(text) to anon, authenticated;

-- جداول لا يلمسها العميل إطلاقاً. بدون سياسة = منع كامل، لكن يجب تفعيل RLS
-- صراحةً وإلا صارت مفتوحة للقراءة والكتابة بمفتاح anon.
alter table public.purchases       enable row level security;
alter table public.promo_grants    enable row level security;
alter table public.billing_events  enable row level security;
alter table public.admin_actions   enable row level security;
alter table public.market_override enable row level security;

-- =============================================================================
--  المهام الدورية
-- =============================================================================
select cron.unschedule('sntp-recompute') where exists (select 1 from cron.job where jobname = 'sntp-recompute');
select cron.schedule('sntp-recompute', '*/5 * * * *', $$select public.recompute_all_markets()$$);

-- لقطة نهائية كل يوم عند 23:50 بتوقيت الجزائر
select cron.unschedule('sntp-daily-snapshot') where exists (select 1 from cron.job where jobname = 'sntp-daily-snapshot');
select cron.schedule('sntp-daily-snapshot', '50 22 * * *', $$select public.recompute_all_markets()$$);

-- =============================================================================
--  تنظيف: حذف التصويتات الأقدم من 30 يوماً (خصوصية وحجم)
-- =============================================================================
select cron.unschedule('sntp-prune') where exists (select 1 from cron.job where jobname = 'sntp-prune');
select cron.schedule('sntp-prune', '30 2 * * *', $$
    -- Measured from the observation, not from arrival: the promise made to users
    -- and in the privacy policy is that raw votes are deleted 30 days after they
    -- were recorded. Pruning on server_time kept a vote's coordinates longer
    -- whenever the phone had been offline.
    delete from public.reports
     where public.observed_at(client_time, server_time) < now() - interval '30 days';
$$);
