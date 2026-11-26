-- Выполнить в редакторе SQL после setup.sql. Повторный запуск безопасен.
create table if not exists public.cars (
    id uuid primary key default gen_random_uuid(),
    brand text not null,
    model text not null,
    price_per_day integer not null check (price_per_day > 0),
    features text not null default '',
    available boolean not null default true,
    created_at timestamptz not null default now()
);
alter table public.cars enable row level security;
grant select on public.cars to authenticated;
drop policy if exists "cars authenticated select" on public.cars;
create policy "cars authenticated select" on public.cars for select to authenticated using (available = true);
-- Пример данных для демонстрации; администратор может заменить их своими.
insert into public.cars (brand, model, price_per_day, features)
select 'Toyota', 'Camry', 4500, 'Автомат · 5 мест · Кондиционер'
where not exists (select 1 from public.cars where brand = 'Toyota' and model = 'Camry');
insert into public.cars (brand, model, price_per_day, features)
select 'Kia', 'Rio', 2800, 'Автомат · 5 мест · Эконом'
where not exists (select 1 from public.cars where brand = 'Kia' and model = 'Rio');
