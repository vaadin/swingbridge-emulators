# Decision-ID map: numbers → slugs

The decision logs used monotonic numeric IDs (`D26`, `SD17`, `M1D10`) and the hard rules used
`R1`–`R13`. Both are now content-derived slugs. The change was made because monotonic allocation
**clashes across git worktrees**: two branches each append "the next" number and both are right
until they meet. A slug is derived from what the decision says, so two branches collide only when
they have actually decided the same thing — which is a merge conflict worth having.

This table exists so an old ID still lands somewhere. Commit messages, PR descriptions, and
`ideas/` drafts written before the rename all speak the old language; nothing rewrites those.

Anchors are now explicit — an `<a id>` tag carrying the slug verbatim — rather than derived from heading text,
so rewording a heading no longer breaks every link to it.


## emulators/decisions.md

| was | is | note |
|---|---|---|
| `D1` | `D_import_swap_target` |  |
| `D2` | `D_peer_terminology` |  |
| `D3` | `D_own_awt_package` |  |
| `D4` | `D_peer_ctor_injection` |  |
| `D5` | `D_never_fail_on_gaps` | MERGE? refined by D_gap_severity_triage (D26) |
| `D6` | `D_best_effort_behaviour` | MERGE? == R4 / M1D8 |
| `D7` | `D_pixel_layout_not_planned` | renamed in the consolidation pass — the old title named a scope line that `R_layouts_close_enough` owns |
| `D8` | `D_single_ui_per_session` | MERGE? == M1D5 |
| `D9` | `D_blocking_dialogs_deferred` | superseded by D30; already a pointer |
| `D10` | `D_pure_java_artifact` + `D_tests_in_java` | **split** in the consolidation pass — one slug held two questions, and only the `src/main` half was ever about consumer classpaths |
| `D11` | `D_generated_stubs` |  |
| `D12` | `D_awt_peer_hooking_rejected` |  |
| `D13` | `D_whitelist_porting` |  |
| `D14` | `D_event_port_policy` | MERGE? == M1D4 |
| `D15` | `D_generator_own_module` |  |
| `D16` | `D_ehelper_statics` |  |
| `D17` | `D_peer_emulator_mapping` |  |
| `D18` | `D_layout_css_on_content` |  |
| `D19` | `D_callswing_funnel` | MERGE? == R8 |
| `D20` | `D_notify_from_attach` |  |
| `D21` | `D_no_silent_improvements` | MERGE? == M1D7 (near-verbatim) |
| `D22` | `D_sync_reads_raw_model` |  |
| `D23` | `D_rootpane_holder` | superseded by D93 + D57; already a pointer |
| `D24` | `D_emulator_surrogate_split` |  |
| `D25` | `D_surrogate_first` |  |
| `D26` | `D_gap_severity_triage` |  |
| `D27` | `D_icon_rendering` |  |
| `D28` | `D_jcombobox` |  |
| `D29` | `D_menu_tree` |  |
| `D30` | `D_callswing_loom` |  |
| `D31` | `D_joptionpane` |  |
| `D31a` | `D_joptionpane_parks_on_jdialog` |  |
| `D31b` | `D_joptionpane_icon_glyph` |  |
| `D31c` | `D_joptionpane_layout` |  |
| `D31d` | `D_joptionpane_rebuild_on_reuse` |  |
| `D32` | `D_timer_swingworker` |  |
| `D32a` | `D_worker_pools_per_ui` | stance later revised by D59 |
| `D32b` | `D_edt_delivery` |  |
| `D32c` | `D_edt_blocking_throws` |  |
| `D32d` | `D_worker_ui_at_construction` |  |
| `D33` | `D_jtable` |  |
| `D33a` | `D_jtable_type_ports` |  |
| `D33b` | `D_jtable_thin_shell` |  |
| `D33c` | `D_jtable_column_model_bridge` |  |
| `D33d` | `D_jtable_renderer_registry` |  |
| `D33e` | `D_jtableheader_holder` |  |
| `D33f` | `D_jtable_cell_editing` |  |
| `D34` | `D_jframe_as_route` |  |
| `D34a` | `D_mainwindow_discovery` |  |
| `D34b` | `D_frame_strategy` |  |
| `D34c` | `D_close_operation_dispatch` |  |
| `D34d` | `D_framework_peer_selection` |  |
| `D34e` | `D_mainwindow_route` |  |
| `D34f` | `D_inline_route_sizing` |  |
| `D34g` | `D_shutdown_lifecycle` |  |
| `D35` | `D_jscrollpane` |  |
| `D35a` | `D_jscrollpane_thin_shell` |  |
| `D35b` | `D_scrollbar_policy_mapping` |  |
| `D35c` | `D_viewport_scrollbar_shadows` |  |
| `D35d` | `D_shadow_children_tree_shape` |  |
| `D36` | `D_box_family` |  |
| `D36a` | `D_peer_content_element_public` |  |
| `D36b` | `D_box_no_surrogate` |  |
| `D36c` | `D_box_align_self_deferred` |  |
| `D37` | `D_gridbaglayout` |  |
| `D37a` | `D_gridbagconstraints_stays_jdk` |  |
| `D37b` | `D_gridbag_no_surrogate` |  |
| `D37c` | `D_gridbag_relative_remainder` |  |
| `D37d` | `D_gridbag_weight_aggregation` |  |
| `D37e` | `D_gridbag_anchor_fill` |  |
| `D37f` | `D_gridbag_insets_ipad` |  |
| `D38` | `D_jformattedtextfield` |  |
| `D38a` | `D_formatted_strategy_interface` |  |
| `D38b` | `D_formatted_peer_family` |  |
| `D38c` | `D_formatter_is_framework_config` |  |
| `D38d` | `D_formatter_swap_rules` |  |
| `D38e` | `D_jtextfield_ctor_widening` |  |
| `D38f` | `D_jformattedtextfield_lockdown` |  |
| `D39` | `D_jspinner_editor` |  |
| `D39a` | `D_spinner_synthetic_field` |  |
| `D39b` | `D_spinner_pattern_on_surrogate` |  |
| `D39c` | `D_dateeditor_preconfigures` |  |
| `D39d` | `D_geteditor_null_until_set` |  |
| `D39e` | `D_jspinner_lockdown` |  |
| `D40` | `D_buttongroup` |  |
| `D40a` | `D_buttongroup_cross_package` |  |
| `D40b` | `D_buttongroup_selection_storage` |  |
| `D40c` | `D_abstractbutton_group_hook` |  |
| `D40d` | `D_buttongroup_coordination` |  |
| `D40e` | `D_buttongroup_browser_click` |  |
| `D40f` | `D_buttongroup_class_shape` |  |
| `D41` | `D_jradiobutton` |  |
| `D41a` | `D_abstractbutton_mixin_dispatch` |  |
| `D41b` | `D_jradiobutton_lockdown` |  |
| `D41c` | `D_vaadin_radiobutton_rehost` |  |
| `D42` | `D_jeditorpane` |  |
| `D42a` | `D_jeditorpane_keeps_peer_ctor` |  |
| `D42b` | `D_document_rte_sync` |  |
| `D42c` | `D_direct_document_mutation` |  |
| `D42d` | `D_setpage_fetch` |  |
| `D42e` | `D_jeditorpane_editable` |  |
| `D43` | `D_jtoolbar` |  |
| `D44` | `D_jsplitpane` |  |
| `D44b` | `D_jsplitpane_slot_tree_shape` | no D44a exists |
| `D45` | `D_jtabbedpane` |  |
| `D46` | `D_jlist` |  |
| `D47` | `D_clipboard` |  |
| `D47a` | `D_clipboard_vt_park` | COLLISION: "D47a" used twice; this is the first |
| `D47a-bis` | `D_clipboard_future_bridge` | COLLISION: labelled "D47a bis" — manual fix, no unique token |
| `D47b` | `D_clipboard_throw_warn_split` |  |
| `D47c` | `D_clipboard_helper_js` |  |
| `D47d` | `D_clipboard_image_png` |  |
| `D47e` | `D_clipboard_test_mode` |  |
| `D48` | `D_jpopupmenu` |  |
| `D49` | `D_drag_and_drop` |  |
| `D49a` | `D_dnd_in_app_scope` |  |
| `D49b` | `D_dnd_transferhandler_only` |  |
| `D49c` | `D_dnd_wiring_paths` |  |
| `D49d` | `D_dnd_semantic_mappings` |  |
| `D49e` | `D_dnd_envelope_teardown` |  |
| `D49f` | `D_dnd_graduations` |  |
| `D50` | `D_jtree` |  |
| `D50a` | `D_jtree_two_layer_resource` |  |
| `D50b` | `D_jtree_renderer_registry` |  |
| `D50c` | `D_jtree_type_ports` |  |
| `D50d` | `D_jtree_field_shadows` |  |
| `D50e` | `D_jtree_row_path_bridge` |  |
| `D50f` | `D_jtree_row_dnd` |  |
| `D50g` | `D_jtree_rename_in_place` |  |
| `D51` | `D_file_dialogs` |  |
| `D51a` | `D_file_load_path` |  |
| `D51b` | `D_file_save_two_dialog` |  |
| `D51c` | `D_ui_scheduler` |  |
| `D51d` | `D_jfilechooser_park` |  |
| `D51e` | `D_file_dialogs_lockdown` |  |
| `D51f` | `D_showdialog_throws` |  |
| `D51g` | `D_save_binds_download` |  |
| `D51h` | `D_showsavedialog_return_ignored` |  |
| `D52` | `D_toolkit_full_surface` |  |
| `D52a` | `D_toolkit_singleton` | COLLISION: "D52a" used twice; this is the first |
| `D52a-bis` | `D_gettoolkit_returns_emulator` | COLLISION: labelled "D52a bis" — manual fix, no unique token |
| `D52b` | `D_toolkit_five_tiers` |  |
| `D52c` | `D_toolkit_surface_and_bodies` |  |
| `D52d` | `D_toolkit_from_client_details` |  |
| `D52e` | `D_toolkit_screen_size` |  |
| `D53` | `D_jcolorchooser` |  |
| `D53a` | `D_jcolorchooser_lockdown` |  |
| `D53b` | `D_colorchooser_model_reused` |  |
| `D53c` | `D_colorchooser_blocking_statics` |  |
| `D53d` | `D_colorchooser_parent_narrowed` |  |
| `D53e` | `D_colorchooser_panels_dropped` |  |
| `D54` | `D_preferences` |  |
| `D54a` | `D_prefs_spi` |  |
| `D54b` | `D_prefs_localstorage` |  |
| `D54c` | `D_prefs_warm_once` |  |
| `D54d` | `D_prefs_first_read_context` |  |
| `D54e` | `D_prefs_abstract_tree` |  |
| `D54f` | `D_prefs_systemroot_deferred` |  |
| `D54g` | `D_prefs_scope_split` | COLLISION: "D54g" used twice; this is the entry |
| `D54g-support` | `D_prefs_lock_safe_getters` | COLLISION: the "D54g support —" bullet — manual fix |
| `D54h` | `D_prefs_swingworker_bridge` |  |
| `D55` | `D_printing` |  |
| `D55a` | `D_printerjob_single_swap` |  |
| `D55b` | `D_handcrafted_graphics_carveout` |  |
| `D55c` | `D_printing_optional_module` |  |
| `D55d` | `D_pdf_engine` |  |
| `D55e` | `D_print_job_ui_capture` |  |
| `D55f` | `D_printing_divergences` |  |
| `D56` | `D_jwindow` |  |
| `D56a` | `D_jwindow_lockdown` |  |
| `D56b` | `D_no_shared_owner_frame` |  |
| `D56c` | `D_window_geometry` |  |
| `D56d` | `D_setundecorated_real` |  |
| `D56e` | `D_window_scaffold_mirrored` |  |
| `D57` | `D_glasspane_structural` |  |
| `D58` | `D_internal_frames` |  |
| `D58a` | `D_jinternalframe_shell` |  |
| `D58b` | `D_internalframe_scaffold` |  |
| `D58c` | `D_internalframe_vetoable` |  |
| `D58d` | `D_internalframe_close_listener` |  |
| `D58e` | `D_internalframe_maximize` |  |
| `D58f` | `D_internalframe_minimize` |  |
| `D58g` | `D_jdesktoppane_holder` |  |
| `D59` | `D_session_scoped_pools` |  |
| `D60` | `D_active_ui_pointer` |  |
| `D61` | `D_gridlayout` |  |
| `D62` | `D_grouplayout` |  |
| `D63` | `D_default_font` |  |
| `D64` | `D_date_emulators` |  |
| `D65` | `D_jseparator` |  |
| `D66` | `D_jtextpane` |  |
| `D67` | `D_htmleditorkit` |  |
| `D68` | `D_htmldocument` |  |
| `D69` | `D_focus_managers` |  |
| `D70` | `D_awt_button` |  |
| `D71` | `D_awt_label` |  |
| `D72` | `D_r12_provenance` |  |
| `D73` | `D_document_last_word` |  |
| `D74` | `D_awt_dead_hooks` |  |
| `D75` | `D_r12_mechanized` |  |
| `D76` | `D_awt_choice` |  |
| `D77` | `D_awt_checkbox` |  |
| `D78` | `D_awt_panel` |  |
| `D79` | `D_awt_scrollbar` |  |
| `D80` | `D_awt_list` |  |
| `D81` | `D_awt_scrollpane` |  |
| `D82` | `D_event_consume` |  |
| `D83` | `D_window_registry` |  |
| `D84` | `D_frame_state_pairs` |  |
| `D85` | `D_window_fanout` |  |
| `D86` | `D_visibility_direction` |  |
| `D87` | `D_window_displayable` |  |
| `D88` | `D_property_fanout_audit` |  |
| `D89` | `D_owed_events` |  |
| `D90` | `D_missing_constants` |  |
| `D91` | `D_reverse_fanout_rows` |  |
| `D92` | `D_event_value_audit` |  |
| `D93` | `D_rootpane_containment` |  |
| `D94` | `D_r12_jdk_typed_half` |  |

## surrogates/decisions.md

| was | is | note |
|---|---|---|
| `SD1` | `SD_mixin_mechanism` |  |
| `SD2` | `SD_naming` |  |
| `SD3` | `SD_shelper_statics` |  |
| `SD4` | `SD_dropin_stance` |  |
| `SD5` | `SD_event_port_stance` | superseded by SD9; already a pointer |
| `SD6` | `SD_api_three_buckets` |  |
| `SD7` | `SD_size_cursor_from_vaadin` | superseded by SD9 |
| `SD8` | `SD_awt_listener_storage` | superseded by SD9; file position is out of numeric order |
| `SD9` | `SD_vaadin_first_binding` |  |
| `SD10` | `SD_border_css_lossy` |  |
| `SD11` | `SD_listeners_without_analog` |  |
| `SD12` | `SD_top_level_ancestor` |  |
| `SD13` | `SD_sjslider` |  |
| `SD14` | `SD_auto_pce` |  |
| `SD15` | `SD_sjspinner` |  |
| `SD15a` | `SD_spinner_date_pattern` |  |
| `SD16` | `SD_key_events` |  |
| `SD17` | `SD_sframe` |  |
| `SD18` | `SD_sjframe` | root-pane half superseded by SD60 |
| `SD19` | `SD_sjbutton` |  |
| `SD20` | `SD_sjlabel` |  |
| `SD21` | `SD_toggle_checkbox_first_cut` | superseded by SD46 |
| `SD22` | `SD_sjpanel` |  |
| `SD23` | `SD_sjtextfield` |  |
| `SD24` | `SD_sjpasswordfield` |  |
| `SD25` | `SD_sjtextarea` |  |
| `SD26` | `SD_sjcombobox` |  |
| `SD27` | `SD_sjmenubar` |  |
| `SD28` | `SD_sjdialog` |  |
| `SD28a` | `SD_swindow_class` |  |
| `SD28b` | `SD_awt_dialog_materialises` |  |
| `SD28c` | `SD_modal_park` | also the target of the D28c typo at emulators/decisions.md:687 |
| `D28c` | `SD_modal_park` | TYPO FIX: emulators/decisions.md:687 says (D28c), means SD28c |
| `SD28d` | `SD_jdialog_lockdown` |  |
| `SD28e` | `SD_window_state_to_swindow` |  |
| `SD28f` | `SD_window_listeners_inlined` |  |
| `SD29` | `SD_no_sjoptionpane` |  |
| `SD30` | `SD_sjtable` |  |
| `SD30a` | `SD_sjtable_row_index_identity` |  |
| `SD30b` | `SD_sjtable_vaadin_rendering` |  |
| `SD30c` | `SD_sjtable_is_model_listener` |  |
| `SD30d` | `SD_sjtable_selection_bridge` |  |
| `SD30e` | `SD_getselectionmodel_deferred` |  |
| `SD30f` | `SD_sjtable_auto_columns` |  |
| `SD30g` | `SD_sjtable_sorting` |  |
| `SD31` | `SD_sjscrollpane` |  |
| `SD32` | `SD_sjformatted_family` |  |
| `SD32a` | `SD_formatted_no_base_class` |  |
| `SD32b` | `SD_formatted_native_value` |  |
| `SD32c` | `SD_sjformattedfieldmixin` |  |
| `SD32d` | `SD_maskformatter_regex` |  |
| `SD32e` | `SD_formatted_listener_storage` |  |
| `SD33` | `SD_browser_timezone` |  |
| `SD34` | `SD_sjeditorpane_div` | superseded by SD47; already a pointer |
| `SD34a` | *(no target)* | never a definition — a prose mention inside SD34; the sentence now names the design instead |
| `SD35` | `SD_sjtoolbar` |  |
| `SD36` | `SD_sjsplitpane` |  |
| `SD37` | `SD_sjtabbedpane` |  |
| `SD38` | `SD_sjlist` |  |
| `SD39` | `SD_sjpopupmenu` |  |
| `SD40` | `SD_sjtree` |  |
| `SD40a` | `SD_sjtree_node_identity` |  |
| `SD40b` | `SD_sjtree_lazy_provider` |  |
| `SD40c` | `SD_sjtree_is_model_listener` |  |
| `SD40d` | `SD_sjtree_selection_bridge` |  |
| `SD40e` | `SD_sjtree_expansion` |  |
| `SD40f` | `SD_sjtree_hierarchy_render` |  |
| `SD40g` | `SD_sjtree_row_api` |  |
| `SD41` | `SD_sjcolorchooser` |  |
| `SD41a` | `SD_colorchooser_input_peer` |  |
| `SD41b` | `SD_colorchooser_model_truth` |  |
| `SD41c` | `SD_colorchooser_hex_bridge` |  |
| `SD41d` | `SD_colorchooser_panels_dropped` |  |
| `SD42` | `SD_sjwindow` |  |
| `SD42a` | `SD_sjwindow_ctor_defaults` |  |
| `SD42b` | `SD_rootpane_scaffold` |  |
| `SD42c` | `SD_swindow_geometry` |  |
| `SD42d` | `SD_undecorated_chrome` |  |
| `SD43` | `SD_glasspane_structural` |  |
| `SD44` | `SD_sjinternalframe` |  |
| `SD44a` | `SD_internalframe_chrome` |  |
| `SD44b` | `SD_internalframe_fire_seams` |  |
| `SD44c` | `SD_internalframe_activation` |  |
| `SD44d` | `SD_internalframe_maximize` |  |
| `SD44e` | `SD_scaffold_fourth_consumer` |  |
| `SD44f` | `SD_no_surrogate_iconify` |  |
| `SD45` | `SD_sjseparator` |  |
| `SD46` | `SD_sjtogglebutton_button_peer` |  |
| `SD47` | `SD_sjeditorpane_rte` |  |
| `SD47a` | `SD_hyperlink_listener` |  |
| `SD47b` | `SD_add_css_rule` |  |
| `SD47c` | `SD_setpage_audit` |  |
| `SD48` | `SD_caret_selection` |  |
| `SD49` | `SD_focus_tracker` |  |
| `SD50` | `SD_sbutton` |  |
| `SD51` | `SD_slabel` |  |
| `SD52` | `SD_schoice` |  |
| `SD53` | `SD_scheckbox` |  |
| `SD54` | `SD_no_spanel` |  |
| `SD55` | `SD_sscrollbar` |  |
| `SD56` | `SD_slist` |  |
| `SD57` | `SD_sscrollpane` |  |
| `SD58` | `SD_no_invented_events` |  |
| `SD59` | `SD_property_fanout_audit` |  |
| `SD60` | `SD_sjrootpane_structural` |  |
| `SD61` | `SD_reverse_fanout_rows` |  |

## migration/1-swing-to-emulators/decisions.md

| was | is | note |
|---|---|---|
| `M1D1` | `M1D_import_swap` | MERGE? == D1 |
| `M1D2` | `M1D_runtime_contract` | MERGE? == D5 + D26 |
| `M1D3` | `M1D_import_rewrite_scope` |  |
| `M1D4` | `M1D_event_port_policy` | MERGE? == D14 |
| `M1D5` | `M1D_single_ui_per_session` | MERGE? == D8 |
| `M1D6` | `M1D_mainwindow_split` |  |
| `M1D7` | `M1D_no_silent_improvements` | MERGE? == D21 (near-verbatim) |
| `M1D8` | `M1D_layout_close_enough` | MERGE? == D7 |
| `M1D9` | `M1D_custom_layoutmanager` |  |
| `M1D10` | `M1D_static_taxonomy` |  |
| `M1D11` | `M1D_addon_packaging` |  |
| `M1D12` | `M1D_swap_vs_reimplement` |  |
| `M1D13` | `M1D_addon_migration_docs` |  |
| `M1D14` | `M1D_single_instance_guards` |  |
| `M1D15` | `M1D_platform_checks_shellouts` |  |
| `M1D16` | `M1D_former_singletons` |  |
| `M1D17` | `M1D_lsp_prerequisite` |  |
| `M1D18` | `M1D_static_sweep_allowlist` |  |

## CLAUDE.md hard rules

| was | is | note |
|---|---|---|
| `R1` | `R_pure_java_library` |  |
| `R2` | `R_java_karibu_tests` | renamed in the consolidation pass — the rule said Kotlin; both premises for that had decayed (`D_tests_in_java`) |
| `R3` | `R_match_swing_errors` |  |
| `R4` | `R_best_effort_behaviour` |  |
| `R5` | `R_layouts_close_enough` |  |
| `R6` | `R_infra_not_surface` |  |
| `R7` | `R_swing_is_truth` |  |
| `R8` | `R_callswing_envelope` |  |
| `R9` | `R_vaadin_first` |  |
| `R10` | `R_leaf_peer_lockdown` |  |
| `R11` | `R_no_spi_selfregister` |  |
| `R12` | `R_no_vaadin_in_api` |  |
| `R13` | `R_decline_effect_only` |  |

## placeholders in ideas/ — resolve, do not map

| was | is | note |
|---|---|---|
| `D7x` | `(delete)` | ideas/awt-textarea.md placeholder; slugs remove the need |
| `SD5x` | `(delete)` | ideas/awt-textarea.md placeholder; slugs remove the need |

## slug → slug renames

A slug is meant to be stable once published, so this table should stay very short. An entry lands
here only when the thing a slug *names* was itself renamed and the sweep of every reference was
deliberate — `DecisionIdTest` enforces the sweep in both directions, so the old form is gone from
the tree and only older commits and PRs still speak it.

| was | is | note |
|---|---|---|
| `M1D_sovbootstrap_canonical` | `M1D_bootstrap_canonical` | the class it names became `SwingBridgeEmulatorsBootstrap` in the 2026-09 rename (`D_naming_scheme`) |
| `D_kit_dryrun_residue` | `D_guide_round_residue` | the skills it names became `/guide-migrateapp` + `/guide-docfix` ("the guide loop", one pass a "round") on 2026-09-28 — "dryrun" misdescribed a real migration and collided with `import-swap --dry-run` |
