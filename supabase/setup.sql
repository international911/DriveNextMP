-- Выполнить один раз в Supabase SQL Editor; service_role ключ в приложение не добавлять.
-- Таблица дополнительных данных пользователя; id связан с аккаунтом Supabase Auth.
create table if not exists public.profiles (
    -- При удалении пользователя его профиль удалится автоматически.
    id uuid primary key references auth.users(id) on delete cascade,
    -- Обязательные фамилия и имя; отчество можно оставить пустым.
    last_name text not null,
    first_name text not null,
    middle_name text,
    -- Дата рождения и пол хранятся в типизированных полях базы.
    birth_date date not null,
    gender text not null check (gender in ('male', 'female')),
    -- База повторно проверяет номер прав, даже если клиентская проверка обойдена.
    license_number text not null check (license_number ~ '^[0-9]{10}$'),
    license_issue_date date not null,
    -- Сохраняем пути к файлам, а не сами изображения в таблице.
    avatar_path text,
    license_photo_path text not null,
    passport_photo_path text not null,
    created_at timestamptz not null default now()
);

-- RLS ограничивает доступ к строкам таблицы на стороне сервера.
alter table public.profiles enable row level security;
revoke all on table public.profiles from anon, authenticated;
grant select, insert, update on table public.profiles to authenticated;
-- Читать, создавать и менять профиль может только его владелец.
create policy "profile owner select" on public.profiles for select to authenticated using (id = (select auth.uid()));
create policy "profile owner insert" on public.profiles for insert to authenticated with check (id = (select auth.uid()));
create policy "profile owner update" on public.profiles for update to authenticated using (id = (select auth.uid())) with check (id = (select auth.uid()));

-- Закрытая папка Storage: изображения не больше 6 МБ, только поддержанные форматы.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('registration-documents', 'registration-documents', false, 6291456, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update set public = false, file_size_limit = 6291456,
    allowed_mime_types = array['image/jpeg', 'image/png', 'image/webp'];

-- Первую папку пути сравниваем с ID вошедшего пользователя.
create policy "documents owner select" on storage.objects for select to authenticated
using (bucket_id = 'registration-documents' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "documents owner insert" on storage.objects for insert to authenticated
with check (bucket_id = 'registration-documents' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "documents owner update" on storage.objects for update to authenticated
using (bucket_id = 'registration-documents' and (storage.foldername(name))[1] = (select auth.uid())::text)
with check (bucket_id = 'registration-documents' and (storage.foldername(name))[1] = (select auth.uid())::text);
