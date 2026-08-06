package studio.ainovations.callguard.ui

/**
 * Shared Compose semantics test tags. Production composables and
 * `androidTest/.../ui/RuleWizardTest.kt` use the same identifiers instead of
 * duplicating string literals.
 */
object CallGuardTestTags {
    const val ADD_RULE_BUTTON = "add_rule_button"
    const val RULE_LIST_ITEM_PREFIX = "rule_list_item_"
    const val RULE_TOGGLE_PREFIX = "rule_toggle_"
    const val RULE_DELETE_PREFIX = "rule_delete_"
    const val RULE_EDIT_PREFIX = "rule_edit_"
    const val RULE_LIST_SCREENING_STATUS = "rule_list_screening_status"
    const val RULE_DELETE_CONFIRM_BUTTON = "rule_delete_confirm_button"

    const val MATCHER_TYPE_CHIP_PREFIX = "matcher_type_chip_"
    const val ACTION_CHIP_PREFIX = "action_chip_"
    const val WIZARD_RAW_VALUE_FIELD = "wizard_raw_value_field"
    const val WIZARD_RANGE_START_FIELD = "wizard_range_start_field"
    const val WIZARD_RANGE_END_FIELD = "wizard_range_end_field"
    const val WIZARD_SPECIFIC_NUMBERS_FIELD = "wizard_specific_numbers_field"
    const val WIZARD_NAME_FIELD = "wizard_name_field"
    const val WIZARD_COUNTRY_FIELD = "wizard_country_field"
    const val WIZARD_PRIORITY_FIELD = "wizard_priority_field"
    const val WIZARD_VALIDATION_ERROR = "wizard_validation_error"
    const val WIZARD_BROAD_REGEX_WARNING = "wizard_broad_regex_warning"
    const val WIZARD_CONFLICT_WARNING_PREFIX = "wizard_conflict_warning_"
    const val WIZARD_POSITIVE_EXAMPLE = "wizard_positive_example"
    const val WIZARD_NEGATIVE_EXAMPLE = "wizard_negative_example"
    const val WIZARD_CONTACTS_PERMISSION_WARNING = "wizard_contacts_permission_warning"
    const val WIZARD_CONTACTS_PERMISSION_BUTTON = "wizard_contacts_permission_button"
    const val WIZARD_SAVE_BUTTON = "wizard_save_button"
    const val WIZARD_CANCEL_BUTTON = "wizard_cancel_button"
    const val WIZARD_TITLE = "wizard_title"

    const val PREVIEW_INPUT_FIELD = "preview_input_field"
    const val PREVIEW_TEST_BUTTON = "preview_test_button"
    const val PREVIEW_RESULT_ACTION = "preview_result_action"
    const val PREVIEW_RESULT_RULE_ID = "preview_result_rule_id"
    const val PREVIEW_RESULT_EXPLANATION = "preview_result_explanation"
    const val PREVIEW_ERROR = "preview_error"
    const val PREVIEW_NOTICE = "preview_notice"
    const val PREVIEW_STALE = "preview_stale"

    const val SETTINGS_BUTTON = "settings_button"
    const val SETTINGS_CONTACTS_PERMISSION_STATUS = "settings_contacts_permission_status"
    const val SETTINGS_CONTACTS_PERMISSION_WARNING = "settings_contacts_permission_warning"
    const val SETTINGS_CONTACTS_REPAIR_BUTTON = "settings_contacts_repair_button"
    const val SETTINGS_SCREENING_ROLE_STATUS = "settings_screening_role_status"
    const val SETTINGS_SCREENING_ROLE_BUTTON = "settings_screening_role_button"
    const val SETTINGS_BACK_BUTTON = "settings_back_button"
}
