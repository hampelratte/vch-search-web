<#include "header.ftl">
<#include "status_messages.ftl">
<#include "navigation.ftl">

<h1>${TITLE}</h1>
<#include "status_messages.ftl">

<script type="text/javascript">
<!--
function showDetails(parser, uri, isVideoPage) {
    $('#details').html('<h1>${I18N_LOADING}</h1><img src="/static/icons/loadingAnimation.gif" alt=""/>');
    $.ajax({
        url: '${ACTION}',
        type: 'GET',
        data: {
            id : parser,
            uri : uri,
            isVideoPage : isVideoPage,
            action : 'parse'
        },
        dataType: 'json',
        timeout: 30000,
        error: function(xhr, text, error) {
            $('#details').html('');
            var output_text = '<strong>' + xhr.status + ' - ' + xhr.statusText + '</strong>';
            if(!xhr.responseText.indexOf('<html>') == 0) {
                output_text += '<br/>' + xhr.responseText;
            } 
            $.pnotify( {
                pnotify_title : '${I18N_ERROR}',
                pnotify_text : output_text,
                pnotify_type : 'error',
                pnotify_hide: false
            });
        },
        success: function(response) {
            var video = response.video;
            var actions = response.actions;

            var html = '<h1>' + video.title + '</h1>';

            // add a preview, if available
            if(video.thumb) {
                html += '<p><img src="'+video.thumb+'" alt="Preview" class="thumb ui-widget-content ui-corner-all" /></p>';
            }

            // add the pubdate, if available
            if(video.pubDate) {
                var date = new Date();
                date.setTime(video.pubDate);
                html += '<p><strong>' + date.toLocaleString();
            }

            // add the duration, if available
            if(video.duration) {
                html += ' - ';
                var secs = parseInt(video.duration);
                if(secs < 60) {
                    html += secs + ' ${I18N_SECONDS}';                        
                } else {
                    var minutes = Math.floor(secs / 60);
                    if(minutes < 10) minutes = "0"+minutes;
                    var secs = secs % 60;
                    if(secs < 10) secs = "0"+secs;
                    html += minutes+':'+secs + ' ${I18N_MINUTES}'; 
                }
            }

            html += '</strong></p>';

            // add the description, if available
            if(video.desc) {
                html += '<p>' + video.desc + '</p>';
            } 

            // add web actions
            if(video.video && actions) {
                for(var i=0; i<actions.length; i++) {
                    html += '<a style="margin-right: 1em;" id="action'+i+'" href="'+actions[i].uri+'">'+actions[i].title+'</a>';
                }
            }

            // display the details
            $('#details').html(html);
            if(video.video) {
                $('#watch').button( {icons: { primary: 'ui-icon-play'}} );
                if(actions) {
                    for(var i=0; i<actions.length; i++) {
                        $('#action'+i).button();           
                    }
                }
            }
            $('#open').button( {icons: { primary: 'ui-icon-extlink'}} );

        }
    });
}
-->
</script>

<div id="search_bar">
    <form action="${ACTION}">
        <input type="text" id="search_input" name="q" class="ui-widget ui-widget-content ui-corner-all" <#if Q??>value="${Q}"</#if> />
        <input type="hidden" name="action" value="search" />
        <input type="submit" name="submit" value="${I18N_DO_SEARCH}" class="ui-button" />
    </form>
    <#if COUNT??>
        <small>${I18N_HITS}: ${COUNT}</small>
    </#if>
</div>

<div id="search_container">
    <#if RESULTS?? && (RESULTS.pages?size > 0)>
    <div id="provider_list">
        <ul>
        <#list RESULTS.pages as provider>
        <li>
            <a href="javascript:void(0)" onclick="$('#results div.result-page').hide(0, function() {$('#provider_${provider_index}').show(0);});">${provider.title} (${provider.pages?size})</a>
        </li>
        </#list>    
        </ul>
    </div>
    <div id="results">
        <#list RESULTS.pages as provider>
        <div id="provider_${provider_index}" class="result-page" style="display:none;" >
            <h2>${provider.title}</h2>
            <ul>
            <#list provider.pages as result>
                <li>
                    <#if result.thumbnail??>
                        <img alt="Preview" src="${result.thumbnail}" class="search-thumb ui-widget-content ui-corner-all" />
                    </#if>
                    
                    <#if result.duration??>
                       <#assign isVideoPage=true>
                    <#else>
                        <#assign isVideoPage=false>
                    </#if>
                    <h4><a href="javascript:showDetails('${result.parser}', '${result.uri?js_string}', ${isVideoPage?string})">${result.title}</a></h4>
                    <#if result.publishDate??>
                        <p>${result.publishDate.time?date?string.short}
                        <#if result.duration?? && (result.duration > 0)>
                            <#assign minutes=result.duration/60 />
                            <#assign seconds=result.duration%60 />
                            - ${minutes?string("00")}:${seconds?string("00")} min
                        </#if>
                        </p>
                    </#if>
                    <#if result.description??>
                        <p>
                        <#assign maxlength=800>
                        <#if (result.description?length > maxlength)>
                            ${result.description?substring(0,maxlength)}...
                        <#else>
                            ${result.description}
                        </#if>
                        </p>
                    </#if>
                    <div style="clear: both; display: block"></div>
                </li>
            </#list>
            </ul>
        </div>
        </#list>
    </div>
    <div id="details"></div>
    </#if>
</div>
<#include "footer.ftl">