var $d723bea02f3e2567$exports = require("./DOMFunctions.main.js");
var $9e20cff0af27e8cc$exports = require("./platform.main.js");
var $2308dc377e184bb0$exports = require("./keyboard.main.js");
var $4gIVO$react = require("react");
var $4gIVO$reactariassr = require("@react-aria/ssr");


function $parcel$export(e, n, v, s) {
  Object.defineProperty(e, n, {get: v, set: s, enumerable: true, configurable: true});
}

$parcel$export(module.exports, "useViewportSize", () => $8b24bab62f5c65ad$export$d699905dd57c73ca);
/*
 * Copyright 2020 Adobe. All rights reserved.
 * This file is licensed to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 * OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */ 




let $8b24bab62f5c65ad$var$visualViewport = typeof document !== 'undefined' && window.visualViewport;
function $8b24bab62f5c65ad$export$d699905dd57c73ca() {
    let isSSR = (0, $4gIVO$reactariassr.useIsSSR)();
    let [size, setSize] = (0, $4gIVO$react.useState)(()=>isSSR ? {
            width: 0,
            height: 0
        } : $8b24bab62f5c65ad$var$getViewportSize());
    (0, $4gIVO$react.useEffect)(()=>{
        let updateSize = (newSize)=>{
            setSize((size)=>{
                if (newSize.width === size.width && newSize.height === size.height) return size;
                return newSize;
            });
        };
        // Use visualViewport api to track available height even on iOS virtual keyboard opening
        let onResize = ()=>{
            // Ignore updates when zoomed.
            if ($8b24bab62f5c65ad$var$visualViewport && $8b24bab62f5c65ad$var$visualViewport.scale > 1) return;
            updateSize($8b24bab62f5c65ad$var$getViewportSize());
        };
        // When closing the keyboard, iOS does not fire the visual viewport resize event until the animation is complete.
        // We can anticipate this and resize early by handling the blur event and using the layout size.
        let frame;
        let onBlur = (e)=>{
            if ($8b24bab62f5c65ad$var$visualViewport && $8b24bab62f5c65ad$var$visualViewport.scale > 1) return;
            if ((0, $2308dc377e184bb0$exports.willOpenKeyboard)((0, $d723bea02f3e2567$exports.getEventTarget)(e))) // Wait one frame to see if a new element gets focused.
            frame = requestAnimationFrame(()=>{
                let activeElement = (0, $d723bea02f3e2567$exports.getActiveElement)();
                if (!activeElement || !(0, $2308dc377e184bb0$exports.willOpenKeyboard)(activeElement)) updateSize({
                    width: document.documentElement.clientWidth,
                    height: document.documentElement.clientHeight
                });
            });
        };
        updateSize($8b24bab62f5c65ad$var$getViewportSize());
        if ((0, $9e20cff0af27e8cc$exports.isIOS)()) window.addEventListener('blur', onBlur, true);
        if (!$8b24bab62f5c65ad$var$visualViewport) window.addEventListener('resize', onResize);
        else $8b24bab62f5c65ad$var$visualViewport.addEventListener('resize', onResize);
        return ()=>{
            cancelAnimationFrame(frame);
            if ((0, $9e20cff0af27e8cc$exports.isIOS)()) window.removeEventListener('blur', onBlur, true);
            if (!$8b24bab62f5c65ad$var$visualViewport) window.removeEventListener('resize', onResize);
            else $8b24bab62f5c65ad$var$visualViewport.removeEventListener('resize', onResize);
        };
    }, []);
    return size;
}
function $8b24bab62f5c65ad$var$getViewportSize() {
    return {
        // Multiply by the visualViewport scale to get the "natural" size, unaffected by pinch zooming.
        width: $8b24bab62f5c65ad$var$visualViewport ? $8b24bab62f5c65ad$var$visualViewport.width * $8b24bab62f5c65ad$var$visualViewport.scale : document.documentElement.clientWidth,
        height: $8b24bab62f5c65ad$var$visualViewport ? $8b24bab62f5c65ad$var$visualViewport.height * $8b24bab62f5c65ad$var$visualViewport.scale : document.documentElement.clientHeight
    };
}


//# sourceMappingURL=useViewportSize.main.js.map
