-- Схема бронирований и автомобилей пользователей; текущий клиент к этим таблицам не обращается.
-- При развёртывании выполнить после setup.sql и catalog.sql в Supabase SQL Editor.
-- Без этого скрипта оформление аренды и подключение автомобиля не смогут сохранять данные.
alter table public.cars add column if not exists location text not null default 'Адрес уточняется';
alter table public.cars add column if not exists insurance_per_day integer not null default 500 check (insurance_per_day >= 0);
alter table public.cars add column if not exists deposit integer not null default 5000 check (deposit >= 0);

create table if not exists public.bookings (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    car_id uuid not null references public.cars(id),
    start_date date not null,
    end_date date not null,
    rental_cost integer not null check (rental_cost > 0),
    insurance_cost integer not null check (insurance_cost >= 0),
    total_cost integer not null check (total_cost > 0),
    deposit integer not null check (deposit >= 0),
    status text not null default 'active' check (status in ('active','cancelled')),
    created_at timestamptz not null default now(),
    check (end_date >= start_date)
);
create index if not exists bookings_user_created on public.bookings(user_id, created_at desc);
create index if not exists bookings_car_period on public.bookings(car_id, start_date, end_date);
alter table public.bookings enable row level security;
revoke all on public.bookings from anon, authenticated;
grant select on public.bookings to authenticated;
drop policy if exists "booking owner select" on public.bookings;
create policy "booking owner select" on public.bookings for select to authenticated using (user_id = (select auth.uid()));

-- Расчёт выполняется в БД: клиент не может подменить цену или забронировать занятый период.
create or replace function public.create_booking(p_car_id uuid, p_start date, p_end date)
returns public.bookings language plpgsql security definer set search_path = '' as $$
declare v_car public.cars; v_days integer; v_result public.bookings;
begin
    if auth.uid() is null then raise exception 'Войдите в аккаунт'; end if;
    if p_start is null or p_end is null or p_start < current_date or p_end < p_start then
        raise exception 'Выберите корректный период аренды';
    end if;
    select * into v_car from public.cars where id = p_car_id and available = true for update;
    if not found then raise exception 'Автомобиль недоступен'; end if;
    if exists (select 1 from public.bookings where car_id = p_car_id and status = 'active'
        and start_date <= p_end and end_date >= p_start) then
        raise exception 'Автомобиль уже забронирован на эти даты';
    end if;
    v_days := p_end - p_start + 1;
    insert into public.bookings(user_id, car_id, start_date, end_date, rental_cost,
        insurance_cost, total_cost, deposit)
    values (auth.uid(), p_car_id, p_start, p_end,
        v_days * v_car.price_per_day, v_days * v_car.insurance_per_day,
        v_days * (v_car.price_per_day + v_car.insurance_per_day), v_car.deposit)
    returning * into v_result;
    return v_result;
end $$;
revoke all on function public.create_booking(uuid,date,date) from public;
grant execute on function public.create_booking(uuid,date,date) to authenticated;

create or replace function public.cancel_booking(p_booking_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
    update public.bookings set status = 'cancelled'
    where id = p_booking_id and user_id = auth.uid() and status = 'active';
    if not found then raise exception 'Активное бронирование не найдено'; end if;
end $$;
revoke all on function public.cancel_booking(uuid) from public;
grant execute on function public.cancel_booking(uuid) to authenticated;

create table if not exists public.user_cars (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    address text not null,
    year integer not null check (year between 1950 and 2100),
    brand text not null,
    model text not null,
    transmission text not null,
    mileage integer not null check (mileage >= 0),
    description text not null,
    photo_paths text[] not null default '{}',
    status text not null default 'draft' check (status in ('draft','approved')),
    created_at timestamptz not null default now(),
    check (cardinality(photo_paths) <= 5)
);
alter table public.user_cars enable row level security;
revoke all on public.user_cars from anon, authenticated;
grant select, insert, update on public.user_cars to authenticated;
drop policy if exists "user cars owner select" on public.user_cars;
drop policy if exists "user cars owner insert" on public.user_cars;
drop policy if exists "user cars owner update" on public.user_cars;
create policy "user cars owner select" on public.user_cars for select to authenticated using (owner_id = (select auth.uid()));
create policy "user cars owner insert" on public.user_cars for insert to authenticated with check (owner_id = (select auth.uid()));
create policy "user cars owner update" on public.user_cars for update to authenticated
using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values ('car-photos','car-photos',false,6291456,array['image/jpeg','image/png','image/webp'])
on conflict(id) do update set public=false,file_size_limit=6291456,
    allowed_mime_types=array['image/jpeg','image/png','image/webp'];
drop policy if exists "car photos owner select" on storage.objects;
drop policy if exists "car photos owner insert" on storage.objects;
drop policy if exists "car photos owner update" on storage.objects;
create policy "car photos owner select" on storage.objects for select to authenticated
using (bucket_id='car-photos' and (storage.foldername(name))[1]=(select auth.uid())::text);
create policy "car photos owner insert" on storage.objects for insert to authenticated
with check (bucket_id='car-photos' and (storage.foldername(name))[1]=(select auth.uid())::text);
create policy "car photos owner update" on storage.objects for update to authenticated
using (bucket_id='car-photos' and (storage.foldername(name))[1]=(select auth.uid())::text)
with check (bucket_id='car-photos' and (storage.foldername(name))[1]=(select auth.uid())::text);
