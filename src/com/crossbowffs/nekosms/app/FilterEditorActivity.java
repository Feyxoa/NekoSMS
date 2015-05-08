package com.crossbowffs.nekosms.app;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import com.crossbowffs.nekosms.R;
import com.crossbowffs.nekosms.data.SmsFilterData;
import com.crossbowffs.nekosms.data.SmsFilterField;
import com.crossbowffs.nekosms.data.SmsFilterLoader;
import com.crossbowffs.nekosms.data.SmsFilterMode;
import com.crossbowffs.nekosms.provider.NekoSmsContract;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FilterEditorActivity extends Activity {
    private EditText mPatternEditText;
    private Spinner mFieldSpinner;
    private Spinner mModeSpinner;
    private CheckBox mIgnoreCaseCheckBox;
    private Uri mFilterUri;
    private SmsFilterData mFilter;
    private EnumAdapter<SmsFilterField> mSmsFilterFieldAdapter;
    private EnumAdapter<SmsFilterMode> mSmsFilterModeAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filter_editor);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.save_filter);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        Intent intent = getIntent();
        Uri filterUri = intent.getData();

        mFilterUri = filterUri;
        mPatternEditText = (EditText)findViewById(R.id.activity_filter_editor_pattern_edittext);
        mFieldSpinner = (Spinner)findViewById(R.id.activity_filter_editor_field_spinner);
        mModeSpinner = (Spinner)findViewById(R.id.activity_filter_editor_mode_spinner);
        mIgnoreCaseCheckBox = (CheckBox)findViewById(R.id.activity_filter_editor_ignorecase_checkbox);

        mSmsFilterFieldAdapter = new EnumAdapter<SmsFilterField>(this,
            android.R.layout.simple_spinner_dropdown_item, SmsFilterField.class);
        mSmsFilterFieldAdapter.setStringMap(FilterEnumMaps.getFieldMap(this));

        mSmsFilterModeAdapter = new EnumAdapter<SmsFilterMode>(this,
            android.R.layout.simple_spinner_dropdown_item, SmsFilterMode.class);
        mSmsFilterModeAdapter.setStringMap(FilterEnumMaps.getModeMap(this));

        mFieldSpinner.setAdapter(mSmsFilterFieldAdapter);
        mModeSpinner.setAdapter(mSmsFilterModeAdapter);

        if (filterUri != null) {
            SmsFilterData filter = SmsFilterLoader.loadFilter(this, filterUri);
            mFilter = filter;
            mFieldSpinner.setSelection(mSmsFilterFieldAdapter.getPosition(filter.getField()));
            mModeSpinner.setSelection(mSmsFilterModeAdapter.getPosition(filter.getMode()));
            mPatternEditText.setText(filter.getPattern());
            mIgnoreCaseCheckBox.setChecked(!filter.isCaseSensitive());
        } else {
            mFieldSpinner.setSelection(mSmsFilterFieldAdapter.getPosition(SmsFilterField.BODY));
            mModeSpinner.setSelection(mSmsFilterModeAdapter.getPosition(SmsFilterMode.CONTAINS));
            mIgnoreCaseCheckBox.setChecked(true);
        }
    }

    @Override
    public void onBackPressed() {
        saveIfValid();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_filter_editor, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            saveIfValid();
            return true;
        case R.id.menu_item_discard_changes:
            discardAndFinish();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    private void finishTryTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAfterTransition();
        } else {
            finish();
        }
    }

    private int getInvalidPatternStringId() {
        SmsFilterMode mode = (SmsFilterMode)mModeSpinner.getSelectedItem();
        if (mode != SmsFilterMode.REGEX) {
            return 0;
        }

        String pattern = mPatternEditText.getText().toString();
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            return R.string.invalid_pattern_regex;
        }

        return 0;
    }

    private boolean isPatternEmpty() {
        return TextUtils.isEmpty(mPatternEditText.getText());
    }

    private void saveIfValid() {
        if (isPatternEmpty()) {
            discardAndFinish();
            return;
        }

        int invalidPatternStringId = getInvalidPatternStringId();
        if (invalidPatternStringId != 0) {
            showInvalidPatternDialog(invalidPatternStringId);
            return;
        }

        saveAndFinish();
    }

    private void saveAndFinish() {
        Uri filterUri = writeFilterData();
        Toast.makeText(this, R.string.filter_saved, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent();
        intent.setData(filterUri);
        setResult(RESULT_OK, intent);
        finishTryTransition();
    }

    private void discardAndFinish() {
        setResult(RESULT_CANCELED, null);
        finishTryTransition();
    }

    private ContentValues createFilterData() {
        SmsFilterField field = (SmsFilterField)mFieldSpinner.getSelectedItem();
        SmsFilterMode mode = (SmsFilterMode)mModeSpinner.getSelectedItem();
        String pattern = mPatternEditText.getText().toString();
        boolean caseSensitive = !mIgnoreCaseCheckBox.isChecked();

        SmsFilterData data = mFilter;
        if (data == null) {
            data = mFilter = new SmsFilterData();
        }
        data.setField(field);
        data.setMode(mode);
        data.setPattern(pattern);
        data.setCaseSensitive(caseSensitive);
        return data.serialize();
    }

    private Uri writeFilterData() {
        Uri filtersUri = NekoSmsContract.Filters.CONTENT_URI;
        ContentResolver contentResolver = getContentResolver();
        ContentValues values = createFilterData();
        Uri filterUri;
        if (mFilterUri != null) {
            filterUri = mFilterUri;
            int updatedRows = contentResolver.update(filterUri, values, null, null);
            if (updatedRows == 0) {
                filterUri = mFilterUri = contentResolver.insert(filtersUri, values);
            }
            // TODO: Check return value
        } else {
            filterUri = contentResolver.insert(filtersUri, values);
            // TODO: Check return value
        }

        return filterUri;
    }

    private void showInvalidPatternDialog(int messageId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle(R.string.invalid_pattern_title)
            .setMessage(messageId)
            .setIconAttribute(android.R.attr.alertDialogIcon)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.discard, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    discardAndFinish();
                }
            });

        AlertDialog dialog = builder.create();
        dialog.show();
    }
}