-- SP13：商機階段轉換歷史（漏斗停留天數／超時）
create table if not exists opportunity_stage_history (
    id                   bigserial primary key,
    opportunity_id       bigint not null references opportunities (id) on delete cascade,
    from_stage           varchar(32),
    to_stage             varchar(32) not null,
    changed_at           timestamptz not null,
    changed_by_user_id   bigint
);

create index if not exists idx_osh_opportunity on opportunity_stage_history (opportunity_id);
create index if not exists idx_osh_to_stage on opportunity_stage_history (to_stage);

-- 既有商機回填：以 created_at 近似「進入當前階段」時間（from_stage 為 null）
insert into opportunity_stage_history (opportunity_id, from_stage, to_stage, changed_at, changed_by_user_id)
select o.id, null, o.stage, o.created_at, null
from opportunities o
where not exists (
    select 1 from opportunity_stage_history h where h.opportunity_id = o.id
);
