-- ---------------------------------------------------------------------------
-- Reference data: the 59 batches, the reunion, and its ticket types.
--
-- Deliberately no accounts and no passwords here. The first super admin is
-- created at startup from the environment (see BootstrapRunner) so that a
-- credential is never committed to a migration that lives forever in git.
-- ---------------------------------------------------------------------------

-- Batches 1968..2026. The roster estimate follows the school's growth — about
-- 34 students in the first batch, about 130 by 2026 — until the real registers
-- are typed in and the committee replaces these numbers.
insert into batch (year, label, label_bn, roster_estimate)
select y,
       'SSC ' || y,
       'এসএসসি ' || y,
       round(34 + ((y - 1968)::numeric / (2026 - 1968)) * 96)::int
from generate_series(1968, 2026) as y
on conflict (year) do nothing;

-- The event this platform exists to run first.
insert into event (slug, title, title_bn, subtitle, subtitle_bn,
                   starts_at, ends_at, venue, venue_bn, status)
values ('reunion-2027',
        'Grand Reunion 2027',
        'মহা পুনর্মিলনী ২০২৭',
        'Batches 1968 – 2026 · One day, together again',
        'ব্যাচ ১৯৬৮ – ২০২৬ · একটি দিন, আবার একসাথে',
        timestamptz '2027-02-12 09:00:00+06',
        timestamptz '2027-02-12 22:00:00+06',
        'School Campus, Chalitatala, Narail',
        'বিদ্যালয় প্রাঙ্গণ, চলিতাতলা, নড়াইল',
        'OPEN')
on conflict (slug) do nothing;

insert into ticket_type (event_id, code, name, name_bn, note, note_bn, amount_bdt, relation, sort_order)
select e.id, t.code, t.name, t.name_bn, t.note, t.note_bn, t.amount, t.relation, t.sort_order
from event e
         cross join (values ('ALUMNI', 'Alumni', 'প্রাক্তন শিক্ষার্থী',
                             'Registration, lunch, T-shirt, souvenir',
                             'রেজিস্ট্রেশন, দুপুরের খাবার, টি-শার্ট, স্মরণিকা', 1500.00, null, 1),
                            ('SPOUSE', 'Spouse', 'স্বামী / স্ত্রী',
                             'Lunch, souvenir', 'দুপুরের খাবার, স্মরণিকা', 1200.00, 'SPOUSE', 2),
                            ('CHILD', 'Child (5-12 yrs)', 'শিশু (৫–১২ বছর)',
                             'Lunch, kids corner', 'দুপুরের খাবার, শিশু কর্নার', 600.00, 'CHILD', 3),
                            ('CHILD_FREE', 'Child (under 5)', 'শিশু (৫ বছরের নিচে)',
                             'Free - no seat allotted', 'বিনামূল্যে — আলাদা আসন নেই', 0.00, 'CHILD', 4),
                            ('GUEST', 'Other family member', 'পরিবারের অন্য সদস্য',
                             'Parent, sibling or other guest', 'পিতা-মাতা, ভাই-বোন বা অন্য অতিথি', 1200.00, 'OTHER', 5))
    as t(code, name, name_bn, note, note_bn, amount, relation, sort_order)
where e.slug = 'reunion-2027'
on conflict (event_id, code) do nothing;

insert into notice (title, title_bn, body, body_bn, pinned, published_at)
values ('Registration is open',
        'নিবন্ধন শুরু হয়েছে',
        'Find your name, confirm your mobile number, and register. Your batch coordinator verifies each registration by hand.',
        'আপনার নাম খুঁজে নিন, মোবাইল নম্বর নিশ্চিত করুন এবং নিবন্ধন করুন। আপনার ব্যাচ সমন্বয়কারী প্রতিটি নিবন্ধন যাচাই করবেন।',
        true, now()),
       ('Help us find the earliest batches',
        'পুরনো ব্যাচগুলো খুঁজে পেতে সাহায্য করুন',
        'The 1968-1980 registers are the hardest. If you know anyone from those years, share their number.',
        '১৯৬৮–১৯৮০ সালের তালিকা সবচেয়ে কঠিন। ওই বছরগুলোর কাউকে চিনলে তার নম্বর দিন।',
        false, now())
on conflict do nothing;
