-- 통합 테스트마다 Testcontainers DB를 비운다(IntegrationTest).
TRUNCATE review_items, deleted_sources, golden_sets, golden_draft_entries, golden_set_versions,
    evaluation_runs, stage_timings, stage_daily_stats, alert_events, dataset_imports,
    dataset_import_videos, golden_dataset_items, source_results, feature_exports RESTART IDENTITY CASCADE;
