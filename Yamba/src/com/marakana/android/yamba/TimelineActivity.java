/* $Id: $
   Copyright 2013, G. Blake Meike

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package com.marakana.android.yamba;

import com.marakana.android.yamba.svc.YambaService;

import android.os.Bundle;


/**
 *
 * @version $Revision: $
 * @author <a href="mailto:blake.meike@gmail.com">G. Blake Meike</a>
 */
public class TimelineActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_timeline);
    }

	@Override
	protected void onPause() {
		super.onPause();
		YambaService.stopPolling(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		YambaService.startPolling(this);
	}
}
