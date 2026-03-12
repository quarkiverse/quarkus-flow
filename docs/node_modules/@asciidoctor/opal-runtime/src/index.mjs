(function(global_object) {

  // @note
  //   A few conventions for the documentation of this file:
  //   1. Always use "//" (in contrast with "/**/")
  //   2. The syntax used is Yardoc (yardoc.org), which is intended for Ruby (se below)
  //   3. `@param` and `@return` types should be preceded by `JS.` when referring to
  //      JavaScript constructors (e.g. `JS.Function`) otherwise Ruby is assumed.
  //   4. `nil` and `null` being unambiguous refer to the respective
  //      objects/values in Ruby and JavaScript
  //   5. This is still WIP :) so please give feedback and suggestions on how
  //      to improve or for alternative solutions
  //
  //   The way the code is digested before going through Yardoc is a secret kept
  //   in the docs repo (https://github.com/opal/docs/tree/master).

  var console;

  // Detect the global object
  if (typeof(globalThis) !== 'undefined') { global_object = globalThis; }
  else if (typeof(global) !== 'undefined') { global_object = global; }
  else if (typeof(window) !== 'undefined') { global_object = window; }

  // Setup a dummy console object if missing
  if (global_object.console == null) {
    global_object.console = {};
  }

  if (typeof(global_object.console) === 'object') {
    console = global_object.console;
  } else {
    console = {};
  }

  if (!('log' in console)) { console.log = function () {}; }
  if (!('warn' in console)) { console.warn = console.log; }

  if (typeof(global_object.Opal) !== 'undefined') {
    console.warn('Opal already loaded. Loading twice can cause troubles, please fix your setup.');
    return global_object.Opal;
  }

  var nil;

  // The actual class for BasicObject
  var BasicObject;

  // The actual Object class.
  // The leading underscore is to avoid confusion with window.Object()
  var _Object;

  // The actual Module class
  var Module;

  // The actual Class class
  var Class;

  // The Opal.Opal class (helpers etc.)
  var _Opal;

  // The Kernel module
  var Kernel;

  // The Opal object that is exposed globally
  var Opal = global_object.Opal = {};

  // This is a useful reference to global object inside ruby files
  Opal.global = global_object;

  // Configure runtime behavior with regards to require and unsupported features
  Opal.config = {
    missing_require_severity: 'error',        // error, warning, ignore
    unsupported_features_severity: 'warning', // error, warning, ignore
    experimental_features_severity: 'warning',// warning, ignore
    enable_stack_trace: true                  // true, false
  };

  // Minify common function calls
  var $call      = Function.prototype.call;
  var $bind      = Function.prototype.bind;
  var $has_own   = Object.hasOwn || $call.bind(Object.prototype.hasOwnProperty);
  var $set_proto = Object.setPrototypeOf;
  var $slice     = $call.bind(Array.prototype.slice);
  var $splice    = $call.bind(Array.prototype.splice);

  // Nil object id is always 4
  var nil_id = 4;

  // Generates even sequential numbers greater than 4
  // (nil_id) to serve as unique ids for ruby objects
  var unique_id = nil_id;

  // Return next unique id
  function $uid() {
    unique_id += 2;
    return unique_id;
  }  Opal.uid = $uid;

  // Retrieve or assign the id of an object
  Opal.id = function(obj) {
    if (obj.$$is_number) return (obj * 2)+1;
    if (obj.$$id == null) {
      $prop(obj, '$$id', $uid());
    }
    return obj.$$id;
  };

  // Globals table
  var $gvars = Opal.gvars = {};

  // Exit function, this should be replaced by platform specific implementation
  // (See nodejs and chrome for examples)
  Opal.exit = function(status) { if ($gvars.DEBUG) console.log('Exited with status '+status); };

  // keeps track of exceptions for $!
  Opal.exceptions = [];

  // @private
  // Pops an exception from the stack and updates `$!`.
  Opal.pop_exception = function() {
    var exception = Opal.exceptions.pop();
    if (exception) {
      $gvars["!"] = exception;
      $gvars["@"] = exception.$backtrace();
    }
    else {
      $gvars["!"] = $gvars["@"] = nil;
    }
  };

  // A helper function for raising things, that gracefully degrades if necessary
  // functionality is not yet loaded.
  function $raise(klass, message) {
    // Raise Exception, so we can know that something wrong is going on.
    if (!klass) klass = Opal.Exception || Error;

    if (Kernel && Kernel.$raise) {
      if (arguments.length > 2) {
        Kernel.$raise(klass.$new.apply(klass, $slice(arguments, 1)));
      }
      else {
        Kernel.$raise(klass, message);
      }
    }
    else if (!klass.$new) {
      throw new klass(message);
    }
    else {
      throw klass.$new(message);
    }
  }

  function $prop(object, name, initialValue) {
    if (typeof(object) === "string") {
      // Special case for:
      //   s = "string"
      //   def s.m; end
      // String class is the only class that:
      // + compiles to JS primitive
      // + allows method definition directly on instances
      // numbers, true, false and null do not support it.
      object[name] = initialValue;
    } else {
      Object.defineProperty(object, name, {
        value: initialValue,
        enumerable: false,
        configurable: true,
        writable: true
      });
    }
  }

  Opal.prop = $prop;

  // @deprecated
  Opal.defineProperty = Opal.prop;

  Opal.slice = $slice;

  // Helpers
  // -----

  var $truthy = Opal.truthy = function(val) {
    return false !== val && nil !== val && undefined !== val && null !== val && (!(val instanceof Boolean) || true === val.valueOf());
  };

  Opal.falsy = function(val) {
    return !$truthy(val);
  };

  Opal.type_error = function(object, type, method, coerced) {
    object = object.$$class;

    if (coerced && method) {
      coerced = coerced.$$class;
      $raise(Opal.TypeError,
        "can't convert " + object + " into " + type +
        " (" + object + "#" + method + " gives " + coerced + ")"
      );
    } else {
      $raise(Opal.TypeError,
        "no implicit conversion of " + object + " into " + type
      );
    }
  };

  Opal.coerce_to = function(object, type, method, args) {
    var body;

    if (method === 'to_int' && type === Opal.Integer && object.$$is_number)
      return object < 0 ? Math.ceil(object) : Math.floor(object);

    if (method === 'to_str' && type === Opal.String && object.$$is_string)
      return object;

    if (Opal.is_a(object, type)) return object;

    // Fast path for the most common situation
    if (object['$respond_to?'].$$pristine && object.$method_missing.$$pristine) {
      body = object[$jsid(method)];
      if (body == null || body.$$stub) Opal.type_error(object, type);
      return body.apply(object, args);
    }

    if (!object['$respond_to?'](method)) {
      Opal.type_error(object, type);
    }

    if (args == null) args = [];
    return Opal.send(object, method, args);
  };

  Opal.respond_to = function(obj, jsid, include_all) {
    if (obj == null || !obj.$$class) return false;
    include_all = !!include_all;
    var body = obj[jsid];

    if (obj['$respond_to?'].$$pristine) {
      if (typeof(body) === "function" && !body.$$stub) {
        return true;
      }
      if (!obj['$respond_to_missing?'].$$pristine) {
        return Opal.send(obj, obj['$respond_to_missing?'], [jsid.substr(1), include_all]);
      }
    } else {
      return Opal.send(obj, obj['$respond_to?'], [jsid.substr(1), include_all]);
    }
  };

  // TracePoint support
  // ------------------
  //
  // Support for `TracePoint.trace(:class) do ... end`
  Opal.trace_class = false;
  Opal.tracers_for_class = [];

  function invoke_tracers_for_class(klass_or_module) {
    var i, ii, tracer;

    for(i = 0, ii = Opal.tracers_for_class.length; i < ii; i++) {
      tracer = Opal.tracers_for_class[i];
      tracer.trace_object = klass_or_module;
      tracer.block.$call(tracer);
    }
  }

  function handle_autoload(cref, name) {
    if (!cref.$$autoload[name].loaded) {
      cref.$$autoload[name].loaded = true;
      try {
        Opal.Kernel.$require(cref.$$autoload[name].path);
      } catch (e) {
        cref.$$autoload[name].exception = e;
        throw e;
      }
      cref.$$autoload[name].required = true;
      if (cref.$$const[name] != null) {
        cref.$$autoload[name].success = true;
        return cref.$$const[name];
      }
    } else if (cref.$$autoload[name].loaded && !cref.$$autoload[name].required) {
      if (cref.$$autoload[name].exception) { throw cref.$$autoload[name].exception; }
    }
  }

  // Constants
  // ---------
  //
  // For future reference:
  // - The Rails autoloading guide (http://guides.rubyonrails.org/v5.0/autoloading_and_reloading_constants.html)
  // - @ConradIrwin's 2012 post on “Everything you ever wanted to know about constant lookup in Ruby” (http://cirw.in/blog/constant-lookup.html)
  //
  // Legend of MRI concepts/names:
  // - constant reference (cref): the module/class that acts as a namespace
  // - nesting: the namespaces wrapping the current scope, e.g. nesting inside
  //            `module A; module B::C; end; end` is `[B::C, A]`

  // Get the constant in the scope of the current cref
  function const_get_name(cref, name) {
    if (cref) {
      if (cref.$$const[name] != null) { return cref.$$const[name]; }
      if (cref.$$autoload && cref.$$autoload[name]) {
        return handle_autoload(cref, name);
      }
    }
  }

  // Walk up the nesting array looking for the constant
  function const_lookup_nesting(nesting, name) {
    var i, ii, constant;

    if (nesting.length === 0) return;

    // If the nesting is not empty the constant is looked up in its elements
    // and in order. The ancestors of those elements are ignored.
    for (i = 0, ii = nesting.length; i < ii; i++) {
      constant = nesting[i].$$const[name];
      if (constant != null) {
        return constant;
      } else if (nesting[i].$$autoload && nesting[i].$$autoload[name]) {
        return handle_autoload(nesting[i], name);
      }
    }
  }

  // Walk up the ancestors chain looking for the constant
  function const_lookup_ancestors(cref, name) {
    var i, ii, ancestors;

    if (cref == null) return;

    ancestors = $ancestors(cref);

    for (i = 0, ii = ancestors.length; i < ii; i++) {
      if (ancestors[i].$$const && $has_own(ancestors[i].$$const, name)) {
        return ancestors[i].$$const[name];
      } else if (ancestors[i].$$autoload && ancestors[i].$$autoload[name]) {
        return handle_autoload(ancestors[i], name);
      }
    }
  }

  // Walk up Object's ancestors chain looking for the constant,
  // but only if cref is missing or a module.
  function const_lookup_Object(cref, name) {
    if (cref == null || cref.$$is_module) {
      return const_lookup_ancestors(_Object, name);
    }
  }

  // Call const_missing if nothing else worked
  function const_missing(cref, name) {
    return (cref || _Object).$const_missing(name);
  }

  // Look for the constant just in the current cref or call `#const_missing`
  Opal.const_get_local = function(cref, name, skip_missing) {
    var result;

    if (cref == null) return;

    if (cref === '::') cref = _Object;

    if (!cref.$$is_module && !cref.$$is_class) {
      $raise(Opal.TypeError, cref.toString() + " is not a class/module");
    }

    result = const_get_name(cref, name);
    return result != null || skip_missing ? result : const_missing(cref, name);
  };

  // Look for the constant relative to a cref or call `#const_missing` (when the
  // constant is prefixed by `::`).
  Opal.const_get_qualified = function(cref, name, skip_missing) {
    var result, cache, cached, current_version = Opal.const_cache_version;

    if (name == null) {
      // A shortpath for calls like ::String => $$$("String")
      result = const_get_name(_Object, cref);

      if (result != null) return result;
      return Opal.const_get_qualified(_Object, cref, skip_missing);
    }

    if (cref == null) return;

    if (cref === '::') cref = _Object;

    if (!cref.$$is_module && !cref.$$is_class) {
      $raise(Opal.TypeError, cref.toString() + " is not a class/module");
    }

    if ((cache = cref.$$const_cache) == null) {
      $prop(cref, '$$const_cache', Object.create(null));
      cache = cref.$$const_cache;
    }
    cached = cache[name];

    if (cached == null || cached[0] !== current_version) {
      ((result = const_get_name(cref, name))              != null) ||
      ((result = const_lookup_ancestors(cref, name))      != null);
      cache[name] = [current_version, result];
    } else {
      result = cached[1];
    }

    return result != null || skip_missing ? result : const_missing(cref, name);
  };

  // Initialize the top level constant cache generation counter
  Opal.const_cache_version = 1;

  // Look for the constant in the open using the current nesting and the nearest
  // cref ancestors or call `#const_missing` (when the constant has no :: prefix).
  Opal.const_get_relative = function(nesting, name, skip_missing) {
    var cref = nesting[0], result, current_version = Opal.const_cache_version, cache, cached;

    if ((cache = nesting.$$const_cache) == null) {
      $prop(nesting, '$$const_cache', Object.create(null));
      cache = nesting.$$const_cache;
    }
    cached = cache[name];

    if (cached == null || cached[0] !== current_version) {
      ((result = const_get_name(cref, name))              != null) ||
      ((result = const_lookup_nesting(nesting, name))     != null) ||
      ((result = const_lookup_ancestors(cref, name))      != null) ||
      ((result = const_lookup_Object(cref, name))         != null);

      cache[name] = [current_version, result];
    } else {
      result = cached[1];
    }

    return result != null || skip_missing ? result : const_missing(cref, name);
  };

  // Register the constant on a cref and opportunistically set the name of
  // unnamed classes/modules.
  function $const_set(cref, name, value) {
    var new_const = true;

    if (cref == null || cref === '::') cref = _Object;

    if (value.$$is_a_module) {
      if (value.$$name == null || value.$$name === nil) value.$$name = name;
      if (value.$$base_module == null) value.$$base_module = cref;
    }

    cref.$$const = (cref.$$const || Object.create(null));

    if (name in cref.$$const || ("$$autoload" in cref && name in cref.$$autoload)) {
      new_const = false;
    }

    cref.$$const[name] = value;

    // Add a short helper to navigate constants manually.
    // @example
    //   Opal.$$.Regexp.$$.IGNORECASE
    cref.$$ = cref.$$const;

    Opal.const_cache_version++;

    // Expose top level constants onto the Opal object
    if (cref === _Object) Opal[name] = value;

    // Name new class directly onto current scope (Opal.Foo.Baz = klass)
    $prop(cref, name, value);

    if (new_const && cref.$const_added && !cref.$const_added.$$pristine) {
      cref.$const_added(name);
    }

    return value;
  }
  Opal.const_set = $const_set;

  // Get all the constants reachable from a given cref, by default will include
  // inherited constants.
  Opal.constants = function(cref, inherit) {
    if (inherit == null) inherit = true;

    var module, modules = [cref], i, ii, constants = {}, constant;

    if (inherit) modules = modules.concat($ancestors(cref));
    if (inherit && cref.$$is_module) modules = modules.concat([Opal.Object]).concat($ancestors(Opal.Object));

    for (i = 0, ii = modules.length; i < ii; i++) {
      module = modules[i];

      // Do not show Objects constants unless we're querying Object itself
      if (cref !== _Object && module == _Object) break;

      for (constant in module.$$const) {
        constants[constant] = true;
      }
      if (module.$$autoload) {
        for (constant in module.$$autoload) {
          constants[constant] = true;
        }
      }
    }

    return Object.keys(constants);
  };

  // Remove a constant from a cref.
  Opal.const_remove = function(cref, name) {
    Opal.const_cache_version++;

    if (cref.$$const[name] != null) {
      var old = cref.$$const[name];
      delete cref.$$const[name];
      return old;
    }

    if (cref.$$autoload && cref.$$autoload[name]) {
      delete cref.$$autoload[name];
      return nil;
    }

    $raise(Opal.NameError, "constant "+cref+"::"+cref.$name()+" not defined");
  };

  // Generates a function that is a curried const_get_relative.
  Opal.const_get_relative_factory = function(nesting) {
    return function(name, skip_missing) {
      return Opal.$$(nesting, name, skip_missing);
    }
  };

  // Setup some shortcuts to reduce compiled size
  Opal.$$ = Opal.const_get_relative;
  Opal.$$$ = Opal.const_get_qualified;
  Opal.$r = Opal.const_get_relative_factory;

  // Modules & Classes
  // -----------------

  // A `class Foo; end` expression in ruby is compiled to call this runtime
  // method which either returns an existing class of the given name, or creates
  // a new class in the given `base` scope.
  //
  // If a constant with the given name exists, then we check to make sure that
  // it is a class and also that the superclasses match. If either of these
  // fail, then we raise a `TypeError`. Note, `superclass` may be null if one
  // was not specified in the ruby code.
  //
  // We pass a constructor to this method of the form `function ClassName() {}`
  // simply so that classes show up with nicely formatted names inside debuggers
  // in the web browser (or node/sprockets).
  //
  // The `scope` is the current `self` value where the class is being created
  // from. We use this to get the scope for where the class should be created.
  // If `scope` is an object (not a class/module), we simple get its class and
  // use that as the scope instead.
  //
  // @param scope        [Object] where the class is being created
  // @param superclass   [Class,null] superclass of the new class (may be null)
  // @param singleton    [Boolean,null] a true value denotes we want to allocate
  //                                    a singleton
  //
  // @return new [Class]  or existing ruby class
  //
  function $allocate_class(name, superclass, singleton) {
    var klass;

    if (superclass != null && superclass.$$bridge) {
      // Inheritance from bridged classes requires
      // calling original JS constructors
      klass = function() {
        var args = $slice(arguments),
            self = new ($bind.apply(superclass.$$constructor, [null].concat(args)))();

        // and replacing a __proto__ manually
        $set_proto(self, klass.$$prototype);
        return self;
      };
    } else {
      klass = function(){};
    }

    if (name && name !== nil) {
      $prop(klass, 'displayName', '::'+name);
    }

    $prop(klass, '$$name', name);
    $prop(klass, '$$constructor', klass);
    $prop(klass, '$$prototype', klass.prototype);
    $prop(klass, '$$const', {});
    $prop(klass, '$$is_class', true);
    $prop(klass, '$$is_a_module', true);
    $prop(klass, '$$super', superclass);
    $prop(klass, '$$cvars', {});
    $prop(klass, '$$own_included_modules', []);
    $prop(klass, '$$own_prepended_modules', []);
    $prop(klass, '$$ancestors', []);
    $prop(klass, '$$ancestors_cache_version', null);
    $prop(klass, '$$subclasses', []);

    $prop(klass.$$prototype, '$$class', klass);

    // By default if there are no singleton class methods
    // __proto__ is Class.prototype
    // Later singleton methods generate a singleton_class
    // and inject it into ancestors chain
    if (Opal.Class) {
      $set_proto(klass, Opal.Class.prototype);
    }

    if (superclass != null) {
      $set_proto(klass.$$prototype, superclass.$$prototype);

      if (singleton !== true) {
        // Let's not forbid GC from cleaning up our
        // subclasses.
        if (typeof WeakRef !== 'undefined') {
          // First, let's clean up our array from empty objects.
          var i, subclass, rebuilt_subclasses = [];
          for (i = 0; i < superclass.$$subclasses.length; i++) {
            subclass = superclass.$$subclasses[i];
            if (subclass.deref() !== undefined) {
              rebuilt_subclasses.push(subclass);
            }
          }
          // Now, let's add our class.
          rebuilt_subclasses.push(new WeakRef(klass));
          superclass.$$subclasses = rebuilt_subclasses;
        }
        else {
          superclass.$$subclasses.push(klass);
        }
      }

      if (superclass.$$meta) {
        // If superclass has metaclass then we have explicitely inherit it.
        Opal.build_class_singleton_class(klass);
      }
    }

    return klass;
  }  Opal.allocate_class = $allocate_class;


  function find_existing_class(scope, name) {
    // Try to find the class in the current scope
    var klass = const_get_name(scope, name);

    // If the class exists in the scope, then we must use that
    if (klass) {
      // Make sure the existing constant is a class, or raise error
      if (!klass.$$is_class) {
        $raise(Opal.TypeError, name + " is not a class");
      }

      return klass;
    }
  }

  function ensureSuperclassMatch(klass, superclass) {
    if (klass.$$super !== superclass) {
      $raise(Opal.TypeError, "superclass mismatch for class " + klass.$$name);
    }
  }

  Opal.klass = function(scope, superclass, name) {
    var bridged;

    if (scope == null || scope == '::') {
      // Global scope
      scope = _Object;
    } else if (!scope.$$is_class && !scope.$$is_module) {
      // Scope is an object, use its class
      scope = scope.$$class;
    }

    // If the superclass is not an Opal-generated class then we're bridging a native JS class
    if (
      superclass != null && (!superclass.hasOwnProperty || (
        superclass.hasOwnProperty && !superclass.hasOwnProperty('$$is_class')
      ))
    ) {
      if (superclass.constructor && superclass.constructor.name == "Function") {
        bridged = superclass;
        superclass = _Object;
      } else {
        $raise(Opal.TypeError, "superclass must be a Class (" + (
          (superclass.constructor && (superclass.constructor.name || superclass.constructor.$$name)) ||
          typeof(superclass)
        ) + " given)");
      }
    }

    var klass = find_existing_class(scope, name);

    if (klass != null) {
      if (superclass) {
        // Make sure existing class has same superclass
        ensureSuperclassMatch(klass, superclass);
      }
    }
    else {
      // Class doesn't exist, create a new one with given superclass...

      // Not specifying a superclass means we can assume it to be Object
      if (superclass == null) {
        superclass = _Object;
      }

      // Create the class object (instance of Class)
      klass = $allocate_class(name, superclass);
      $const_set(scope, name, klass);

      // Call .inherited() hook with new class on the superclass
      if (superclass.$inherited) {
        superclass.$inherited(klass);
      }

      if (bridged) {
        Opal.bridge(bridged, klass);
      }
    }

    if (Opal.trace_class) { invoke_tracers_for_class(klass); }

    return klass;
  };

  // Define new module (or return existing module). The given `scope` is basically
  // the current `self` value the `module` statement was defined in. If this is
  // a ruby module or class, then it is used, otherwise if the scope is a ruby
  // object then that objects real ruby class is used (e.g. if the scope is the
  // main object, then the top level `Object` class is used as the scope).
  //
  // If a module of the given name is already defined in the scope, then that
  // instance is just returned.
  //
  // If there is a class of the given name in the scope, then an error is
  // generated instead (cannot have a class and module of same name in same scope).
  //
  // Otherwise, a new module is created in the scope with the given name, and that
  // new instance is returned back (to be referenced at runtime).
  //
  // @param  scope [Module, Class] class or module this definition is inside
  // @param  id   [String] the name of the new (or existing) module
  //
  // @return [Module]
  function $allocate_module(name) {
    var constructor = function(){};
    var module = constructor;

    if (name)
      $prop(constructor, 'displayName', name+'.constructor');

    $prop(module, '$$name', name);
    $prop(module, '$$prototype', constructor.prototype);
    $prop(module, '$$const', {});
    $prop(module, '$$is_module', true);
    $prop(module, '$$is_a_module', true);
    $prop(module, '$$cvars', {});
    $prop(module, '$$iclasses', []);
    $prop(module, '$$own_included_modules', []);
    $prop(module, '$$own_prepended_modules', []);
    $prop(module, '$$ancestors', [module]);
    $prop(module, '$$ancestors_cache_version', null);

    $set_proto(module, Opal.Module.prototype);

    return module;
  }  Opal.allocate_module = $allocate_module;

  function find_existing_module(scope, name) {
    var module = const_get_name(scope, name);
    if (module == null && scope === _Object) module = const_lookup_ancestors(_Object, name);

    if (module) {
      if (!module.$$is_module && module !== _Object) {
        $raise(Opal.TypeError, name + " is not a module");
      }
    }

    return module;
  }

  Opal.module = function(scope, name) {
    var module;

    if (scope == null || scope == '::') {
      // Global scope
      scope = _Object;
    } else if (!scope.$$is_class && !scope.$$is_module) {
      // Scope is an object, use its class
      scope = scope.$$class;
    }

    module = find_existing_module(scope, name);

    if (module == null) {
      // Module doesnt exist, create a new one...
      module = $allocate_module(name);
      $const_set(scope, name, module);
    }

    if (Opal.trace_class) { invoke_tracers_for_class(module); }

    return module;
  };

  // Return the singleton class for the passed object.
  //
  // If the given object alredy has a singleton class, then it will be stored on
  // the object as the `$$meta` property. If this exists, then it is simply
  // returned back.
  //
  // Otherwise, a new singleton object for the class or object is created, set on
  // the object at `$$meta` for future use, and then returned.
  //
  // @param object [Object] the ruby object
  // @return [Class] the singleton class for object
  Opal.get_singleton_class = function(object) {
    if (object.$$is_number) {
      $raise(Opal.TypeError, "can't define singleton");
    }
    if (object.$$meta) {
      return object.$$meta;
    }

    if (object.hasOwnProperty('$$is_class')) {
      return Opal.build_class_singleton_class(object);
    } else if (object.hasOwnProperty('$$is_module')) {
      return Opal.build_module_singleton_class(object);
    } else {
      return Opal.build_object_singleton_class(object);
    }
  };

  // helper to set $$meta on klass, module or instance
  function set_meta(obj, meta) {
    if (obj.hasOwnProperty('$$meta')) {
      obj.$$meta = meta;
    } else {
      $prop(obj, '$$meta', meta);
    }
    if (obj.$$frozen) {
      // If a object is frozen (sealed), freeze $$meta too.
      // No need to inject $$meta.$$prototype in the prototype chain,
      // as $$meta cannot be modified anyway.
      obj.$$meta.$freeze();
    } else {
      $set_proto(obj, meta.$$prototype);
    }
  }
  // Build the singleton class for an existing class. Class object are built
  // with their singleton class already in the prototype chain and inheriting
  // from their superclass object (up to `Class` itself).
  //
  // NOTE: Actually in MRI a class' singleton class inherits from its
  // superclass' singleton class which in turn inherits from Class.
  //
  // @param klass [Class]
  // @return [Class]
  Opal.build_class_singleton_class = function(klass) {
    if (klass.$$meta) {
      return klass.$$meta;
    }

    // The singleton_class superclass is the singleton_class of its superclass;
    // but BasicObject has no superclass (its `$$super` is null), thus we
    // fallback on `Class`.
    var superclass = klass === BasicObject ? Class : Opal.get_singleton_class(klass.$$super);

    var meta = $allocate_class(null, superclass, true);

    $prop(meta, '$$is_singleton', true);
    $prop(meta, '$$singleton_of', klass);
    set_meta(klass, meta);
    // Restoring ClassName.class
    $prop(klass, '$$class', Opal.Class);

    return meta;
  };

  Opal.build_module_singleton_class = function(mod) {
    if (mod.$$meta) {
      return mod.$$meta;
    }

    var meta = $allocate_class(null, Opal.Module, true);

    $prop(meta, '$$is_singleton', true);
    $prop(meta, '$$singleton_of', mod);
    set_meta(mod, meta);
    // Restoring ModuleName.class
    $prop(mod, '$$class', Opal.Module);

    return meta;
  };

  // Build the singleton class for a Ruby (non class) Object.
  //
  // @param object [Object]
  // @return [Class]
  Opal.build_object_singleton_class = function(object) {
    var superclass = object.$$class,
        klass = $allocate_class(nil, superclass, true);

    $prop(klass, '$$is_singleton', true);
    $prop(klass, '$$singleton_of', object);

    delete klass.$$prototype.$$class;

    set_meta(object, klass);

    return klass;
  };

  Opal.is_method = function(prop) {
    return (prop[0] === '$' && prop[1] !== '$');
  };

  Opal.instance_methods = function(mod) {
    var exclude = [], results = [], ancestors = $ancestors(mod);

    for (var i = 0, l = ancestors.length; i < l; i++) {
      var ancestor = ancestors[i],
          proto = ancestor.$$prototype;

      if (proto.hasOwnProperty('$$dummy')) {
        proto = proto.$$define_methods_on;
      }

      var props = Object.getOwnPropertyNames(proto);

      for (var j = 0, ll = props.length; j < ll; j++) {
        var prop = props[j];

        if (Opal.is_method(prop)) {
          var method_name = prop.slice(1),
              method = proto[prop];

          if (method.$$stub && exclude.indexOf(method_name) === -1) {
            exclude.push(method_name);
          }

          if (!method.$$stub && results.indexOf(method_name) === -1 && exclude.indexOf(method_name) === -1) {
            results.push(method_name);
          }
        }
      }
    }

    return results;
  };

  Opal.own_instance_methods = function(mod) {
    var results = [],
        proto = mod.$$prototype;

    if (proto.hasOwnProperty('$$dummy')) {
      proto = proto.$$define_methods_on;
    }

    var props = Object.getOwnPropertyNames(proto);

    for (var i = 0, length = props.length; i < length; i++) {
      var prop = props[i];

      if (Opal.is_method(prop)) {
        var method = proto[prop];

        if (!method.$$stub) {
          var method_name = prop.slice(1);
          results.push(method_name);
        }
      }
    }

    return results;
  };

  Opal.methods = function(obj) {
    return Opal.instance_methods(obj.$$meta || obj.$$class);
  };

  Opal.own_methods = function(obj) {
    return obj.$$meta ? Opal.own_instance_methods(obj.$$meta) : [];
  };

  Opal.receiver_methods = function(obj) {
    var mod = Opal.get_singleton_class(obj);
    var singleton_methods = Opal.own_instance_methods(mod);
    var instance_methods = Opal.own_instance_methods(mod.$$super);
    return singleton_methods.concat(instance_methods);
  };

  // Returns an object containing all pairs of names/values
  // for all class variables defined in provided +module+
  // and its ancestors.
  //
  // @param module [Module]
  // @return [Object]
  Opal.class_variables = function(module) {
    var ancestors = $ancestors(module),
        i, length = ancestors.length,
        result = {};

    for (i = length - 1; i >= 0; i--) {
      var ancestor = ancestors[i];

      for (var cvar in ancestor.$$cvars) {
        result[cvar] = ancestor.$$cvars[cvar];
      }
    }

    return result;
  };

  // Sets class variable with specified +name+ to +value+
  // in provided +module+
  //
  // @param module [Module]
  // @param name [String]
  // @param value [Object]
  Opal.class_variable_set = function(module, name, value) {
    var ancestors = $ancestors(module),
        i, length = ancestors.length;

    for (i = length - 2; i >= 0; i--) {
      var ancestor = ancestors[i];

      if ($has_own(ancestor.$$cvars, name)) {
        ancestor.$$cvars[name] = value;
        return value;
      }
    }

    module.$$cvars[name] = value;

    return value;
  };

  // Gets class variable with specified +name+ from provided +module+
  //
  // @param module [Module]
  // @param name [String]
  Opal.class_variable_get = function(module, name, tolerant) {
    if ($has_own(module.$$cvars, name))
      return module.$$cvars[name];

    var ancestors = $ancestors(module),
      i, length = ancestors.length;

    for (i = 0; i < length; i++) {
      var ancestor = ancestors[i];

      if ($has_own(ancestor.$$cvars, name)) {
        return ancestor.$$cvars[name];
      }
    }

    if (!tolerant)
      $raise(Opal.NameError, 'uninitialized class variable '+name+' in '+module.$name());

    return nil;
  };

  function isRoot(proto) {
    return proto.hasOwnProperty('$$iclass') && proto.hasOwnProperty('$$root');
  }

  function own_included_modules(module) {
    var result = [], mod, proto = Object.getPrototypeOf(module.$$prototype);

    while (proto) {
      if (proto.hasOwnProperty('$$class')) {
        // superclass
        break;
      }
      mod = protoToModule(proto);
      if (mod) {
        result.push(mod);
      }
      proto = Object.getPrototypeOf(proto);
    }

    return result;
  }

  function own_prepended_modules(module) {
    var result = [], mod, proto = Object.getPrototypeOf(module.$$prototype);

    if (module.$$prototype.hasOwnProperty('$$dummy')) {
      while (proto) {
        if (proto === module.$$prototype.$$define_methods_on) {
          break;
        }

        mod = protoToModule(proto);
        if (mod) {
          result.push(mod);
        }

        proto = Object.getPrototypeOf(proto);
      }
    }

    return result;
  }


  // The actual inclusion of a module into a class.
  //
  // ## Class `$$parent` and `iclass`
  //
  // To handle `super` calls, every class has a `$$parent`. This parent is
  // used to resolve the next class for a super call. A normal class would
  // have this point to its superclass. However, if a class includes a module
  // then this would need to take into account the module. The module would
  // also have to then point its `$$parent` to the actual superclass. We
  // cannot modify modules like this, because it might be included in more
  // then one class. To fix this, we actually insert an `iclass` as the class'
  // `$$parent` which can then point to the superclass. The `iclass` acts as
  // a proxy to the actual module, so the `super` chain can then search it for
  // the required method.
  //
  // @param module [Module] the module to include
  // @param includer [Module] the target class to include module into
  // @return [null]
  Opal.append_features = function(module, includer) {
    var module_ancestors = $ancestors(module);
    var iclasses = [];

    if (module_ancestors.indexOf(includer) !== -1) {
      $raise(Opal.ArgumentError, 'cyclic include detected');
    }

    for (var i = 0, length = module_ancestors.length; i < length; i++) {
      var ancestor = module_ancestors[i], iclass = create_iclass(ancestor);
      $prop(iclass, '$$included', true);
      iclasses.push(iclass);
    }
    var includer_ancestors = $ancestors(includer),
        chain = chain_iclasses(iclasses),
        start_chain_after,
        end_chain_on;

    if (includer_ancestors.indexOf(module) === -1) {
      // first time include

      // includer -> chain.first -> ...chain... -> chain.last -> includer.parent
      start_chain_after = includer.$$prototype;
      end_chain_on = Object.getPrototypeOf(includer.$$prototype);
    } else {
      // The module has been already included,
      // we don't need to put it into the ancestors chain again,
      // but this module may have new included modules.
      // If it's true we need to copy them.
      //
      // The simplest way is to replace ancestors chain from
      //          parent
      //            |
      //   `module` iclass (has a $$root flag)
      //            |
      //   ...previos chain of module.included_modules ...
      //            |
      //  "next ancestor" (has a $$root flag or is a real class)
      //
      // to
      //          parent
      //            |
      //    `module` iclass (has a $$root flag)
      //            |
      //   ...regenerated chain of module.included_modules
      //            |
      //   "next ancestor" (has a $$root flag or is a real class)
      //
      // because there are no intermediate classes between `parent` and `next ancestor`.
      // It doesn't break any prototypes of other objects as we don't change class references.

      var parent = includer.$$prototype, module_iclass = Object.getPrototypeOf(parent);

      while (module_iclass != null) {
        if (module_iclass.$$module === module && isRoot(module_iclass)) {
          break;
        }

        parent = module_iclass;
        module_iclass = Object.getPrototypeOf(module_iclass);
      }

      if (module_iclass) {
        // module has been directly included
        var next_ancestor = Object.getPrototypeOf(module_iclass);

        // skip non-root iclasses (that were recursively included)
        while (next_ancestor.hasOwnProperty('$$iclass') && !isRoot(next_ancestor)) {
          next_ancestor = Object.getPrototypeOf(next_ancestor);
        }

        start_chain_after = parent;
        end_chain_on = next_ancestor;
      } else {
        // module has not been directly included but was in ancestor chain because it was included by another module
        // include it directly
        start_chain_after = includer.$$prototype;
        end_chain_on = Object.getPrototypeOf(includer.$$prototype);
      }
    }

    $set_proto(start_chain_after, chain.first);
    $set_proto(chain.last, end_chain_on);

    // recalculate own_included_modules cache
    includer.$$own_included_modules = own_included_modules(includer);

    Opal.const_cache_version++;
  };

  Opal.prepend_features = function(module, prepender) {
    // Here we change the ancestors chain from
    //
    //   prepender
    //      |
    //    parent
    //
    // to:
    //
    // dummy(prepender)
    //      |
    //  iclass(module)
    //      |
    // iclass(prepender)
    //      |
    //    parent
    var module_ancestors = $ancestors(module);
    var iclasses = [];

    if (module_ancestors.indexOf(prepender) !== -1) {
      $raise(Opal.ArgumentError, 'cyclic prepend detected');
    }

    for (var i = 0, length = module_ancestors.length; i < length; i++) {
      var ancestor = module_ancestors[i], iclass = create_iclass(ancestor);
      $prop(iclass, '$$prepended', true);
      iclasses.push(iclass);
    }

    var chain = chain_iclasses(iclasses),
        dummy_prepender = prepender.$$prototype,
        previous_parent = Object.getPrototypeOf(dummy_prepender),
        prepender_iclass,
        start_chain_after,
        end_chain_on;

    if (dummy_prepender.hasOwnProperty('$$dummy')) {
      // The module already has some prepended modules
      // which means that we don't need to make it "dummy"
      prepender_iclass = dummy_prepender.$$define_methods_on;
    } else {
      // Making the module "dummy"
      prepender_iclass = create_dummy_iclass(prepender);
      flush_methods_in(prepender);
      $prop(dummy_prepender, '$$dummy', true);
      $prop(dummy_prepender, '$$define_methods_on', prepender_iclass);

      // Converting
      //   dummy(prepender) -> previous_parent
      // to
      //   dummy(prepender) -> iclass(prepender) -> previous_parent
      $set_proto(dummy_prepender, prepender_iclass);
      $set_proto(prepender_iclass, previous_parent);
    }

    var prepender_ancestors = $ancestors(prepender);

    if (prepender_ancestors.indexOf(module) === -1) {
      // first time prepend

      start_chain_after = dummy_prepender;

      // next $$root or prepender_iclass or non-$$iclass
      end_chain_on = Object.getPrototypeOf(dummy_prepender);
      while (end_chain_on != null) {
        if (
          end_chain_on.hasOwnProperty('$$root') ||
          end_chain_on === prepender_iclass ||
          !end_chain_on.hasOwnProperty('$$iclass')
        ) {
          break;
        }

        end_chain_on = Object.getPrototypeOf(end_chain_on);
      }
    } else {
      $raise(Opal.RuntimeError, "Prepending a module multiple times is not supported");
    }

    $set_proto(start_chain_after, chain.first);
    $set_proto(chain.last, end_chain_on);

    // recalculate own_prepended_modules cache
    prepender.$$own_prepended_modules = own_prepended_modules(prepender);

    Opal.const_cache_version++;
  };

  function flush_methods_in(module) {
    var proto = module.$$prototype,
        props = Object.getOwnPropertyNames(proto);

    for (var i = 0; i < props.length; i++) {
      var prop = props[i];
      if (Opal.is_method(prop)) {
        delete proto[prop];
      }
    }
  }

  function create_iclass(module) {
    var iclass = create_dummy_iclass(module);

    if (module.$$is_module) {
      module.$$iclasses.push(iclass);
    }

    return iclass;
  }

  // Dummy iclass doesn't receive updates when the module gets a new method.
  function create_dummy_iclass(module) {
    var iclass = {},
        proto = module.$$prototype;

    if (proto.hasOwnProperty('$$dummy')) {
      proto = proto.$$define_methods_on;
    }

    var props = Object.getOwnPropertyNames(proto),
        length = props.length, i;

    for (i = 0; i < length; i++) {
      var prop = props[i];
      $prop(iclass, prop, proto[prop]);
    }

    $prop(iclass, '$$iclass', true);
    $prop(iclass, '$$module', module);

    return iclass;
  }

  function chain_iclasses(iclasses) {
    var length = iclasses.length, first = iclasses[0];

    $prop(first, '$$root', true);

    if (length === 1) {
      return { first: first, last: first };
    }

    var previous = first;

    for (var i = 1; i < length; i++) {
      var current = iclasses[i];
      $set_proto(previous, current);
      previous = current;
    }


    return { first: iclasses[0], last: iclasses[length - 1] };
  }

  // For performance, some core Ruby classes are toll-free bridged to their
  // native JavaScript counterparts (e.g. a Ruby Array is a JavaScript Array).
  //
  // This method is used to setup a native constructor (e.g. Array), to have
  // its prototype act like a normal Ruby class. Firstly, a new Ruby class is
  // created using the native constructor so that its prototype is set as the
  // target for the new class. Note: all bridged classes are set to inherit
  // from Object.
  //
  // Example:
  //
  //    Opal.bridge(self, Function);
  //
  // @param klass       [Class] the Ruby class to bridge
  // @param constructor [JS.Function] native JavaScript constructor to use
  // @return [Class] returns the passed Ruby class
  //
  Opal.bridge = function(native_klass, klass) {
    if (native_klass.hasOwnProperty('$$bridge')) {
      $raise(Opal.ArgumentError, "already bridged");
    }

    // constructor is a JS function with a prototype chain like:
    // - constructor
    //   - super
    //
    // What we need to do is to inject our class (with its prototype chain)
    // between constructor and super. For example, after injecting ::Object
    // into JS String we get:
    //
    // - constructor (window.String)
    //   - Opal.Object
    //     - Opal.Kernel
    //       - Opal.BasicObject
    //         - super (window.Object)
    //           - null
    //
    $prop(native_klass, '$$bridge', klass);
    $set_proto(native_klass.prototype, (klass.$$super || Opal.Object).$$prototype);
    $prop(klass, '$$prototype', native_klass.prototype);

    $prop(klass.$$prototype, '$$class', klass);
    $prop(klass, '$$constructor', native_klass);
    $prop(klass, '$$bridge', true);
  };

  function protoToModule(proto) {
    if (proto.hasOwnProperty('$$dummy')) {
      return;
    } else if (proto.hasOwnProperty('$$iclass')) {
      return proto.$$module;
    } else if (proto.hasOwnProperty('$$class')) {
      return proto.$$class;
    }
  }

  function own_ancestors(module) {
    return module.$$own_prepended_modules.concat([module]).concat(module.$$own_included_modules);
  }

  // The Array of ancestors for a given module/class
  function $ancestors(module) {
    if (!module) { return []; }

    if (module.$$ancestors_cache_version === Opal.const_cache_version) {
      return module.$$ancestors;
    }

    var result = [], i, mods, length;

    for (i = 0, mods = own_ancestors(module), length = mods.length; i < length; i++) {
      result.push(mods[i]);
    }

    if (module.$$super) {
      for (i = 0, mods = $ancestors(module.$$super), length = mods.length; i < length; i++) {
        result.push(mods[i]);
      }
    }

    module.$$ancestors_cache_version = Opal.const_cache_version;
    module.$$ancestors = result;

    return result;
  }  Opal.ancestors = $ancestors;

  Opal.included_modules = function(module) {
    var result = [], mod = null, proto = Object.getPrototypeOf(module.$$prototype);

    for (; proto && Object.getPrototypeOf(proto); proto = Object.getPrototypeOf(proto)) {
      mod = protoToModule(proto);
      if (mod && mod.$$is_module && proto.$$iclass && proto.$$included) {
        result.push(mod);
      }
    }

    return result;
  };


  // Method Missing
  // --------------

  // Methods stubs are used to facilitate method_missing in opal. A stub is a
  // placeholder function which just calls `method_missing` on the receiver.
  // If no method with the given name is actually defined on an object, then it
  // is obvious to say that the stub will be called instead, and then in turn
  // method_missing will be called.
  //
  // When a file in ruby gets compiled to javascript, it includes a call to
  // this function which adds stubs for every method name in the compiled file.
  // It should then be safe to assume that method_missing will work for any
  // method call detected.
  //
  // Method stubs are added to the BasicObject prototype, which every other
  // ruby object inherits, so all objects should handle method missing. A stub
  // is only added if the given property name (method name) is not already
  // defined.
  //
  // Note: all ruby methods have a `$` prefix in javascript, so all stubs will
  // have this prefix as well (to make this method more performant).
  //
  //    Opal.add_stubs("foo,bar,baz=");
  //
  // All stub functions will have a private `$$stub` property set to true so
  // that other internal methods can detect if a method is just a stub or not.
  // `Kernel#respond_to?` uses this property to detect a methods presence.
  //
  // @param stubs [Array] an array of method stubs to add
  // @return [undefined]
  Opal.add_stubs = function(stubs) {
    var proto = Opal.BasicObject.$$prototype;
    var stub, existing_method;
    stubs = stubs.split(',');

    for (var i = 0, length = stubs.length; i < length; i++) {
      stub = $jsid(stubs[i]), existing_method = proto[stub];

      if (existing_method == null || existing_method.$$stub) {
        Opal.add_stub_for(proto, stub);
      }
    }
  };

  // Add a method_missing stub function to the given prototype for the
  // given name.
  //
  // @param prototype [Prototype] the target prototype
  // @param stub [String] stub name to add (e.g. "$foo")
  // @return [undefined]
  Opal.add_stub_for = function(prototype, stub) {
    // Opal.stub_for(stub) is the method_missing_stub
    $prop(prototype, stub, Opal.stub_for(stub));
  };

  // Generate the method_missing stub for a given method name.
  //
  // @param method_name [String] The js-name of the method to stub (e.g. "$foo")
  // @return [undefined]
  Opal.stub_for = function(method_name) {

    function method_missing_stub() {
      // Copy any given block onto the method_missing dispatcher
      this.$method_missing.$$p = method_missing_stub.$$p;

      // Set block property to null ready for the next call (stop false-positives)
      method_missing_stub.$$p = null;

      // call method missing with correct args (remove '$' prefix on method name)
      var args_ary = new Array(arguments.length);
      for(var i = 0, l = args_ary.length; i < l; i++) { args_ary[i] = arguments[i]; }

      return this.$method_missing.apply(this, [method_name.slice(1)].concat(args_ary));
    }

    method_missing_stub.$$stub = true;

    return method_missing_stub;
  };


  // Methods
  // -------

  // Arity count error dispatcher for methods
  //
  // @param actual [Fixnum] number of arguments given to method
  // @param expected [Fixnum] expected number of arguments
  // @param object [Object] owner of the method +meth+
  // @param meth [String] method name that got wrong number of arguments
  // @raise [ArgumentError]
  Opal.ac = function(actual, expected, object, meth) {
    var inspect = '';
    if (object.$$is_a_module) {
      inspect += object.$$name + '.';
    }
    else {
      inspect += object.$$class.$$name + '#';
    }
    inspect += meth;

    $raise(Opal.ArgumentError, '[' + inspect + '] wrong number of arguments (given ' + actual + ', expected ' + expected + ')');
  };

  // Arity count error dispatcher for blocks
  //
  // @param actual [Fixnum] number of arguments given to block
  // @param expected [Fixnum] expected number of arguments
  // @param context [Object] context of the block definition
  // @raise [ArgumentError]
  Opal.block_ac = function(actual, expected, context) {
    var inspect = "`block in " + context + "'";

    $raise(Opal.ArgumentError, inspect + ': wrong number of arguments (given ' + actual + ', expected ' + expected + ')');
  };

  function get_ancestors(obj) {
    if (obj.hasOwnProperty('$$meta') && obj.$$meta !== null) {
      return $ancestors(obj.$$meta);
    } else {
      return $ancestors(obj.$$class);
    }
  }
  // Super dispatcher
  Opal.find_super = function(obj, mid, current_func, defcheck, allow_stubs) {
    var jsid = $jsid(mid), ancestors, super_method;

    ancestors = get_ancestors(obj);

    var current_index = ancestors.indexOf(current_func.$$owner);

    for (var i = current_index + 1; i < ancestors.length; i++) {
      var ancestor = ancestors[i],
          proto = ancestor.$$prototype;

      if (proto.hasOwnProperty('$$dummy')) {
        proto = proto.$$define_methods_on;
      }

      if (proto.hasOwnProperty(jsid)) {
        super_method = proto[jsid];
        break;
      }
    }

    if (!defcheck && super_method && super_method.$$stub && obj.$method_missing.$$pristine) {
      // method_missing hasn't been explicitly defined
      $raise(Opal.NoMethodError, 'super: no superclass method `'+mid+"' for "+obj, mid);
    }

    return (super_method.$$stub && !allow_stubs) ? null : super_method;
  };

  // Iter dispatcher for super in a block
  Opal.find_block_super = function(obj, jsid, current_func, defcheck, implicit) {
    var call_jsid = jsid;

    if (!current_func) {
      $raise(Opal.RuntimeError, "super called outside of method");
    }

    if (implicit && current_func.$$define_meth) {
      $raise(Opal.RuntimeError,
        "implicit argument passing of super from method defined by define_method() is not supported. " +
        "Specify all arguments explicitly"
      );
    }

    if (current_func.$$def) {
      call_jsid = current_func.$$jsid;
    }

    return Opal.find_super(obj, call_jsid, current_func, defcheck);
  };

  // @deprecated
  Opal.find_super_dispatcher = Opal.find_super;

  // @deprecated
  Opal.find_iter_super_dispatcher = Opal.find_block_super;

  // handles yield calls for 1 yielded arg
  Opal.yield1 = function(block, arg) {
    if (typeof(block) !== "function") {
      $raise(Opal.LocalJumpError, "no block given");
    }

    var has_mlhs = block.$$has_top_level_mlhs_arg,
        has_trailing_comma = block.$$has_trailing_comma_in_args;

    if (block.length > 1 || ((has_mlhs || has_trailing_comma) && block.length === 1)) {
      arg = Opal.to_ary(arg);
    }

    if ((block.length > 1 || (has_trailing_comma && block.length === 1)) && arg.$$is_array) {
      return block.apply(null, arg);
    }
    else {
      return block(arg);
    }
  };

  // handles yield for > 1 yielded arg
  Opal.yieldX = function(block, args) {
    if (typeof(block) !== "function") {
      $raise(Opal.LocalJumpError, "no block given");
    }

    if (block.length > 1 && args.length === 1) {
      if (args[0].$$is_array) {
        return block.apply(null, args[0]);
      }
    }

    if (!args.$$is_array) {
      var args_ary = new Array(args.length);
      for(var i = 0, l = args_ary.length; i < l; i++) { args_ary[i] = args[i]; }

      return block.apply(null, args_ary);
    }

    return block.apply(null, args);
  };

  // Finds the corresponding exception match in candidates.  Each candidate can
  // be a value, or an array of values.  Returns null if not found.
  Opal.rescue = function(exception, candidates) {
    for (var i = 0; i < candidates.length; i++) {
      var candidate = candidates[i];

      if (candidate.$$is_array) {
        var result = Opal.rescue(exception, candidate);

        if (result) {
          return result;
        }
      }
      else if (candidate === Opal.JS.Error || candidate['$==='](exception)) {
        return candidate;
      }
    }

    return null;
  };

  Opal.is_a = function(object, klass) {
    if (klass != null && object.$$meta === klass || object.$$class === klass) {
      return true;
    }

    if (object.$$is_number && klass.$$is_number_class) {
      return (klass.$$is_integer_class) ? (object % 1) === 0 : true;
    }

    var ancestors = $ancestors(object.$$is_class ? Opal.get_singleton_class(object) : (object.$$meta || object.$$class));

    return ancestors.indexOf(klass) !== -1;
  };

  // Helpers for extracting kwsplats
  // Used for: { **h }
  Opal.to_hash = function(value) {
    if (value.$$is_hash) {
      return value;
    }
    else if (value['$respond_to?']('to_hash', true)) {
      var hash = value.$to_hash();
      if (hash.$$is_hash) {
        return hash;
      }
      else {
        $raise(Opal.TypeError, "Can't convert " + value.$$class +
          " to Hash (" + value.$$class + "#to_hash gives " + hash.$$class + ")");
      }
    }
    else {
      $raise(Opal.TypeError, "no implicit conversion of " + value.$$class + " into Hash");
    }
  };

  // Helpers for implementing multiple assignment
  // Our code for extracting the values and assigning them only works if the
  // return value is a JS array.
  // So if we get an Array subclass, extract the wrapped JS array from it

  // Used for: a, b = something (no splat)
  Opal.to_ary = function(value) {
    if (value.$$is_array) {
      return value;
    }
    else if (value['$respond_to?']('to_ary', true)) {
      var ary = value.$to_ary();
      if (ary === nil) {
        return [value];
      }
      else if (ary.$$is_array) {
        return ary;
      }
      else {
        $raise(Opal.TypeError, "Can't convert " + value.$$class +
          " to Array (" + value.$$class + "#to_ary gives " + ary.$$class + ")");
      }
    }
    else {
      return [value];
    }
  };

  // Used for: a, b = *something (with splat)
  Opal.to_a = function(value) {
    if (value.$$is_array) {
      // A splatted array must be copied
      return value.slice();
    }
    else if (value['$respond_to?']('to_a', true)) {
      var ary = value.$to_a();
      if (ary === nil) {
        return [value];
      }
      else if (ary.$$is_array) {
        return ary;
      }
      else {
        $raise(Opal.TypeError, "Can't convert " + value.$$class +
          " to Array (" + value.$$class + "#to_a gives " + ary.$$class + ")");
      }
    }
    else {
      return [value];
    }
  };

  // Used for extracting keyword arguments from arguments passed to
  // JS function. If provided +arguments+ list doesn't have a Hash
  // as a last item, returns a blank Hash.
  //
  // @param parameters [Array]
  // @return [Hash]
  //
  Opal.extract_kwargs = function(parameters) {
    var kwargs = parameters[parameters.length - 1];
    if (kwargs != null && Opal.respond_to(kwargs, '$to_hash', true)) {
      $splice(parameters, parameters.length - 1);
      return kwargs;
    }
  };

  // Used to get a list of rest keyword arguments. Method takes the given
  // keyword args, i.e. the hash literal passed to the method containing all
  // keyword arguemnts passed to method, as well as the used args which are
  // the names of required and optional arguments defined. This method then
  // just returns all key/value pairs which have not been used, in a new
  // hash literal.
  //
  // @param given_args [Hash] all kwargs given to method
  // @param used_args [Object<String: true>] all keys used as named kwargs
  // @return [Hash]
  //
  Opal.kwrestargs = function(given_args, used_args) {
    var keys      = [],
        map       = {},
        key           ,
        given_map = given_args.$$smap;

    for (key in given_map) {
      if (!used_args[key]) {
        keys.push(key);
        map[key] = given_map[key];
      }
    }

    return Opal.hash2(keys, map);
  };

  function apply_blockopts(block, blockopts) {
    if (typeof(blockopts) === 'number') {
      block.$$arity = blockopts;
    }
    else if (typeof(blockopts) === 'object') {
      Object.assign(block, blockopts);
    }
  }

  // Optimization for a costly operation of prepending '$' to method names
  var jsid_cache = {};
  function $jsid(name) {
    return jsid_cache[name] || (jsid_cache[name] = '$' + name);
  }
  Opal.jsid = $jsid;

  // Calls passed method on a ruby object with arguments and block:
  //
  // Can take a method or a method name.
  //
  // 1. When method name gets passed it invokes it by its name
  //    and calls 'method_missing' when object doesn't have this method.
  //    Used internally by Opal to invoke method that takes a block or a splat.
  // 2. When method (i.e. method body) gets passed, it doesn't trigger 'method_missing'
  //    because it doesn't know the name of the actual method.
  //    Used internally by Opal to invoke 'super'.
  //
  // @example
  //   var my_array = [1, 2, 3, 4]
  //   Opal.send(my_array, 'length')                    # => 4
  //   Opal.send(my_array, my_array.$length)            # => 4
  //
  //   Opal.send(my_array, 'reverse!')                  # => [4, 3, 2, 1]
  //   Opal.send(my_array, my_array['$reverse!']')      # => [4, 3, 2, 1]
  //
  // @param recv [Object] ruby object
  // @param method [Function, String] method body or name of the method
  // @param args [Array] arguments that will be passed to the method call
  // @param block [Function] ruby block
  // @param blockopts [Object, Number] optional properties to set on the block
  // @return [Object] returning value of the method call
  Opal.send = function(recv, method, args, block, blockopts) {
    var body;

    if (typeof(method) === 'function') {
      body = method;
      method = null;
    } else if (typeof(method) === 'string') {
      body = recv[$jsid(method)];
    } else {
      $raise(Opal.NameError, "Passed method should be a string or a function");
    }

    return Opal.send2(recv, body, method, args, block, blockopts);
  };

  Opal.send2 = function(recv, body, method, args, block, blockopts) {
    if (body == null && method != null && recv.$method_missing) {
      body = recv.$method_missing;
      args = [method].concat(args);
    }

    apply_blockopts(block, blockopts);

    if (typeof block === 'function') body.$$p = block;
    return body.apply(recv, args);
  };

  Opal.refined_send = function(refinement_groups, recv, method, args, block, blockopts) {
    var i, j, k, ancestors, ancestor, refinements, refinement, refine_modules, refine_module, body;

    ancestors = get_ancestors(recv);

    // For all ancestors that there are, starting from the closest to the furthest...
    for (i = 0; i < ancestors.length; i++) {
      ancestor = Opal.id(ancestors[i]);

      // For all refinement groups there are, starting from the closest scope to the furthest...
      for (j = 0; j < refinement_groups.length; j++) {
        refinements = refinement_groups[j];

        // For all refinements there are, starting from the last `using` call to the furthest...
        for (k = refinements.length - 1; k >= 0; k--) {
          refinement = refinements[k];
          if (typeof refinement.$$refine_modules === 'undefined') continue;

          // A single module being given as an argument of the `using` call contains multiple
          // refinement modules
          refine_modules = refinement.$$refine_modules;

          // Does this module refine a given call for a given ancestor module?
          if (typeof refine_modules[ancestor] === 'undefined') continue;
          refine_module = refine_modules[ancestor];

          // Does this module define a method we want to call?
          if (typeof refine_module.$$prototype[$jsid(method)] !== 'undefined') {
            body = refine_module.$$prototype[$jsid(method)];
            return Opal.send2(recv, body, method, args, block, blockopts);
          }
        }
      }
    }

    return Opal.send(recv, method, args, block, blockopts);
  };

  Opal.lambda = function(block, blockopts) {
    block.$$is_lambda = true;

    apply_blockopts(block, blockopts);

    return block;
  };

  // Used to define methods on an object. This is a helper method, used by the
  // compiled source to define methods on special case objects when the compiler
  // can not determine the destination object, or the object is a Module
  // instance. This can get called by `Module#define_method` as well.
  //
  // ## Modules
  //
  // Any method defined on a module will come through this runtime helper.
  // The method is added to the module body, and the owner of the method is
  // set to be the module itself. This is used later when choosing which
  // method should show on a class if more than 1 included modules define
  // the same method. Finally, if the module is in `module_function` mode,
  // then the method is also defined onto the module itself.
  //
  // ## Classes
  //
  // This helper will only be called for classes when a method is being
  // defined indirectly; either through `Module#define_method`, or by a
  // literal `def` method inside an `instance_eval` or `class_eval` body. In
  // either case, the method is simply added to the class' prototype. A special
  // exception exists for `BasicObject` and `Object`. These two classes are
  // special because they are used in toll-free bridged classes. In each of
  // these two cases, extra work is required to define the methods on toll-free
  // bridged class' prototypes as well.
  //
  // ## Objects
  //
  // If a simple ruby object is the object, then the method is simply just
  // defined on the object as a singleton method. This would be the case when
  // a method is defined inside an `instance_eval` block.
  //
  // @param obj  [Object, Class] the actual obj to define method for
  // @param jsid [String] the JavaScript friendly method name (e.g. '$foo')
  // @param body [JS.Function] the literal JavaScript function used as method
  // @param blockopts [Object, Number] optional properties to set on the body
  // @return [null]
  //
  Opal.def = function(obj, jsid, body, blockopts) {
    apply_blockopts(body, blockopts);

    // Special case for a method definition in the
    // top-level namespace
    if (obj === Opal.top) {
      return Opal.defn(Opal.Object, jsid, body);
    }
    // if instance_eval is invoked on a module/class, it sets inst_eval_mod
    else if (!obj.$$eval && obj.$$is_a_module) {
      return Opal.defn(obj, jsid, body);
    }
    else {
      return Opal.defs(obj, jsid, body);
    }
  };

  // Define method on a module or class (see Opal.def).
  Opal.defn = function(module, jsid, body) {
    $deny_frozen_access(module);

    body.displayName = jsid;
    body.$$owner = module;

    var name = jsid.substr(1);

    var proto = module.$$prototype;
    if (proto.hasOwnProperty('$$dummy')) {
      proto = proto.$$define_methods_on;
    }
    $prop(proto, jsid, body);

    if (module.$$is_module) {
      if (module.$$module_function) {
        Opal.defs(module, jsid, body);
      }

      for (var i = 0, iclasses = module.$$iclasses, length = iclasses.length; i < length; i++) {
        var iclass = iclasses[i];
        $prop(iclass, jsid, body);
      }
    }

    var singleton_of = module.$$singleton_of;
    if (module.$method_added && !module.$method_added.$$stub && !singleton_of) {
      module.$method_added(name);
    }
    else if (singleton_of && singleton_of.$singleton_method_added && !singleton_of.$singleton_method_added.$$stub) {
      singleton_of.$singleton_method_added(name);
    }

    return name;
  };

  // Define a singleton method on the given object (see Opal.def).
  Opal.defs = function(obj, jsid, body, blockopts) {
    apply_blockopts(body, blockopts);

    if (obj.$$is_string || obj.$$is_number) {
      $raise(Opal.TypeError, "can't define singleton");
    }
    return Opal.defn(Opal.get_singleton_class(obj), jsid, body);
  };

  // Called from #remove_method.
  Opal.rdef = function(obj, jsid) {
    if (!$has_own(obj.$$prototype, jsid)) {
      $raise(Opal.NameError, "method '" + jsid.substr(1) + "' not defined in " + obj.$name());
    }

    delete obj.$$prototype[jsid];

    if (obj.$$is_singleton) {
      if (obj.$$prototype.$singleton_method_removed && !obj.$$prototype.$singleton_method_removed.$$stub) {
        obj.$$prototype.$singleton_method_removed(jsid.substr(1));
      }
    }
    else {
      if (obj.$method_removed && !obj.$method_removed.$$stub) {
        obj.$method_removed(jsid.substr(1));
      }
    }
  };

  // Called from #undef_method.
  Opal.udef = function(obj, jsid) {
    if (!obj.$$prototype[jsid] || obj.$$prototype[jsid].$$stub) {
      $raise(Opal.NameError, "method '" + jsid.substr(1) + "' not defined in " + obj.$name());
    }

    Opal.add_stub_for(obj.$$prototype, jsid);

    if (obj.$$is_singleton) {
      if (obj.$$prototype.$singleton_method_undefined && !obj.$$prototype.$singleton_method_undefined.$$stub) {
        obj.$$prototype.$singleton_method_undefined(jsid.substr(1));
      }
    }
    else {
      if (obj.$method_undefined && !obj.$method_undefined.$$stub) {
        obj.$method_undefined(jsid.substr(1));
      }
    }
  };

  function is_method_body(body) {
    return (typeof(body) === "function" && !body.$$stub);
  }

  Opal.alias = function(obj, name, old) {
    var id     = $jsid(name),
        old_id = $jsid(old),
        body,
        alias;

    // Aliasing on main means aliasing on Object...
    if (typeof obj.$$prototype === 'undefined') {
      obj = Opal.Object;
    }

    body = obj.$$prototype[old_id];

    // When running inside #instance_eval the alias refers to class methods.
    if (obj.$$eval) {
      return Opal.alias(Opal.get_singleton_class(obj), name, old);
    }

    if (!is_method_body(body)) {
      var ancestor = obj.$$super;

      while (typeof(body) !== "function" && ancestor) {
        body     = ancestor[old_id];
        ancestor = ancestor.$$super;
      }

      if (!is_method_body(body) && obj.$$is_module) {
        // try to look into Object
        body = Opal.Object.$$prototype[old_id];
      }

      if (!is_method_body(body)) {
        $raise(Opal.NameError, "undefined method `" + old + "' for class `" + obj.$name() + "'");
      }
    }

    // If the body is itself an alias use the original body
    // to keep the max depth at 1.
    if (body.$$alias_of) body = body.$$alias_of;

    // We need a wrapper because otherwise properties
    // would be overwritten on the original body.
    alias = function() {
      var block = alias.$$p, args, i, ii;

      args = new Array(arguments.length);
      for(i = 0, ii = arguments.length; i < ii; i++) {
        args[i] = arguments[i];
      }

      alias.$$p = null;

      return Opal.send(this, body, args, block);
    };

    // Assign the 'length' value with defineProperty because
    // in strict mode the property is not writable.
    // It doesn't work in older browsers (like Chrome 38), where
    // an exception is thrown breaking Opal altogether.
    try {
      Object.defineProperty(alias, 'length', { value: body.length });
    } catch (e) {}

    // Try to make the browser pick the right name
    alias.displayName       = name;

    alias.$$arity           = body.$$arity == null ? body.length : body.$$arity;
    alias.$$parameters      = body.$$parameters;
    alias.$$source_location = body.$$source_location;
    alias.$$alias_of        = body;
    alias.$$alias_name      = name;

    Opal.defn(obj, id, alias);

    return obj;
  };

  Opal.alias_gvar = function(new_name, old_name) {
    Object.defineProperty($gvars, new_name, {
      configurable: true,
      enumerable: true,
      get: function() {
        return $gvars[old_name];
      },
      set: function(new_value) {
        $gvars[old_name] = new_value;
      }
    });
    return nil;
  };

  Opal.alias_native = function(obj, name, native_name) {
    var id   = $jsid(name),
        body = obj.$$prototype[native_name];

    if (typeof(body) !== "function" || body.$$stub) {
      $raise(Opal.NameError, "undefined native method `" + native_name + "' for class `" + obj.$name() + "'");
    }

    Opal.defn(obj, id, body);

    return obj;
  };


  // Hashes
  // ------

  Opal.hash_init = function(hash) {
    hash.$$smap = Object.create(null);
    hash.$$map  = Object.create(null);
    hash.$$keys = [];
  };

  Opal.hash_clone = function(from_hash, to_hash) {
    to_hash.$$none = from_hash.$$none;
    to_hash.$$proc = from_hash.$$proc;

    for (var i = 0, keys = from_hash.$$keys, smap = from_hash.$$smap, len = keys.length, key, value; i < len; i++) {
      key = keys[i];

      if (key.$$is_string) {
        value = smap[key];
      } else {
        value = key.value;
        key = key.key;
      }

      Opal.hash_put(to_hash, key, value);
    }
  };

  Opal.hash_put = function(hash, key, value) {
    if (key.$$is_string) {
      if (!$has_own(hash.$$smap, key)) {
        hash.$$keys.push(key);
      }
      hash.$$smap[key] = value;
      return;
    }

    var key_hash, bucket, last_bucket;
    key_hash = hash.$$by_identity ? Opal.id(key) : key.$hash();

    if (!$has_own(hash.$$map, key_hash)) {
      bucket = {key: key, key_hash: key_hash, value: value};
      hash.$$keys.push(bucket);
      hash.$$map[key_hash] = bucket;
      return;
    }

    bucket = hash.$$map[key_hash];

    while (bucket) {
      if (key === bucket.key || key['$eql?'](bucket.key)) {
        last_bucket = undefined;
        bucket.value = value;
        break;
      }
      last_bucket = bucket;
      bucket = bucket.next;
    }

    if (last_bucket) {
      bucket = {key: key, key_hash: key_hash, value: value};
      hash.$$keys.push(bucket);
      last_bucket.next = bucket;
    }
  };

  Opal.hash_get = function(hash, key) {
    if (key.$$is_string) {
      if ($has_own(hash.$$smap, key)) {
        return hash.$$smap[key];
      }
      return;
    }

    var key_hash, bucket;
    key_hash = hash.$$by_identity ? Opal.id(key) : key.$hash();

    if ($has_own(hash.$$map, key_hash)) {
      bucket = hash.$$map[key_hash];

      while (bucket) {
        if (key === bucket.key || key['$eql?'](bucket.key)) {
          return bucket.value;
        }
        bucket = bucket.next;
      }
    }
  };

  Opal.hash_delete = function(hash, key) {
    var i, keys = hash.$$keys, length = keys.length, value, key_tmp;

    if (key.$$is_string) {
      if (typeof key !== "string") key = key.valueOf();

      if (!$has_own(hash.$$smap, key)) {
        return;
      }

      for (i = 0; i < length; i++) {
        key_tmp = keys[i];

        if (key_tmp.$$is_string && typeof key_tmp !== "string") {
          key_tmp = key_tmp.valueOf();
        }

        if (key_tmp === key) {
          keys.splice(i, 1);
          break;
        }
      }

      value = hash.$$smap[key];
      delete hash.$$smap[key];
      return value;
    }

    var key_hash = key.$hash();

    if (!$has_own(hash.$$map, key_hash)) {
      return;
    }

    var bucket = hash.$$map[key_hash], last_bucket;

    while (bucket) {
      if (key === bucket.key || key['$eql?'](bucket.key)) {
        value = bucket.value;

        for (i = 0; i < length; i++) {
          if (keys[i] === bucket) {
            keys.splice(i, 1);
            break;
          }
        }

        if (last_bucket && bucket.next) {
          last_bucket.next = bucket.next;
        }
        else if (last_bucket) {
          delete last_bucket.next;
        }
        else if (bucket.next) {
          hash.$$map[key_hash] = bucket.next;
        }
        else {
          delete hash.$$map[key_hash];
        }

        return value;
      }
      last_bucket = bucket;
      bucket = bucket.next;
    }
  };

  Opal.hash_rehash = function(hash) {
    for (var i = 0, length = hash.$$keys.length, key_hash, bucket, last_bucket; i < length; i++) {

      if (hash.$$keys[i].$$is_string) {
        continue;
      }

      key_hash = hash.$$keys[i].key.$hash();

      if (key_hash === hash.$$keys[i].key_hash) {
        continue;
      }

      bucket = hash.$$map[hash.$$keys[i].key_hash];
      last_bucket = undefined;

      while (bucket) {
        if (bucket === hash.$$keys[i]) {
          if (last_bucket && bucket.next) {
            last_bucket.next = bucket.next;
          }
          else if (last_bucket) {
            delete last_bucket.next;
          }
          else if (bucket.next) {
            hash.$$map[hash.$$keys[i].key_hash] = bucket.next;
          }
          else {
            delete hash.$$map[hash.$$keys[i].key_hash];
          }
          break;
        }
        last_bucket = bucket;
        bucket = bucket.next;
      }

      hash.$$keys[i].key_hash = key_hash;

      if (!$has_own(hash.$$map, key_hash)) {
        hash.$$map[key_hash] = hash.$$keys[i];
        continue;
      }

      bucket = hash.$$map[key_hash];
      last_bucket = undefined;

      while (bucket) {
        if (bucket === hash.$$keys[i]) {
          last_bucket = undefined;
          break;
        }
        last_bucket = bucket;
        bucket = bucket.next;
      }

      if (last_bucket) {
        last_bucket.next = hash.$$keys[i];
      }
    }
  };

  Opal.hash = function() {
    var arguments_length = arguments.length, args, hash, i, length, key, value;

    if (arguments_length === 1 && arguments[0].$$is_hash) {
      return arguments[0];
    }

    hash = new Opal.Hash();
    Opal.hash_init(hash);

    if (arguments_length === 1) {
      args = arguments[0];

      if (arguments[0].$$is_array) {
        length = args.length;

        for (i = 0; i < length; i++) {
          if (args[i].length !== 2) {
            $raise(Opal.ArgumentError, "value not of length 2: " + args[i].$inspect());
          }

          key = args[i][0];
          value = args[i][1];

          Opal.hash_put(hash, key, value);
        }

        return hash;
      }
      else {
        args = arguments[0];
        for (key in args) {
          if ($has_own(args, key)) {
            value = args[key];

            Opal.hash_put(hash, key, value);
          }
        }

        return hash;
      }
    }

    if (arguments_length % 2 !== 0) {
      $raise(Opal.ArgumentError, "odd number of arguments for Hash");
    }

    for (i = 0; i < arguments_length; i += 2) {
      key = arguments[i];
      value = arguments[i + 1];

      Opal.hash_put(hash, key, value);
    }

    return hash;
  };

  // A faster Hash creator for hashes that just use symbols and
  // strings as keys. The map and keys array can be constructed at
  // compile time, so they are just added here by the constructor
  // function.
  //
  Opal.hash2 = function(keys, smap) {
    var hash = new Opal.Hash();

    hash.$$smap = smap;
    hash.$$map  = Object.create(null);
    hash.$$keys = keys;

    return hash;
  };

  // Create a new range instance with first and last values, and whether the
  // range excludes the last value.
  //
  Opal.range = function(first, last, exc) {
    var range         = new Opal.Range();
        range.begin   = first;
        range.end     = last;
        range.excl    = exc;

    return range;
  };

  var reserved_ivar_names = [
    // properties
    "constructor", "displayName", "__count__", "__noSuchMethod__",
    "__parent__", "__proto__",
    // methods
    "hasOwnProperty", "valueOf"
  ];

  // Get the ivar name for a given name.
  // Mostly adds a trailing $ to reserved names.
  //
  Opal.ivar = function(name) {
    if (reserved_ivar_names.indexOf(name) !== -1) {
      name += "$";
    }

    return name;
  };

  // Support for #freeze
  // -------------------

  // helper that can be used from methods
  function $deny_frozen_access(obj) {
    if (obj.$$frozen) {
      $raise(Opal.FrozenError, "can't modify frozen " + (obj.$class()) + ": " + (obj), Opal.hash2(["receiver"], {"receiver": obj}));
    }
  }  Opal.deny_frozen_access = $deny_frozen_access;

  // common #freeze runtime support
  Opal.freeze = function(obj) {
    $prop(obj, "$$frozen", true);

    // set $$id
    if (!obj.hasOwnProperty('$$id')) { $prop(obj, '$$id', $uid()); }

    if (obj.hasOwnProperty('$$meta')) {
      // freeze $$meta if it has already been set
      obj.$$meta.$freeze();
    } else {
      // ensure $$meta can be set lazily, $$meta is frozen when set in runtime.js
      $prop(obj, '$$meta', null);
    }

    // $$comparable is used internally and set multiple times
    // defining it before sealing ensures it can be modified later on
    if (!obj.hasOwnProperty('$$comparable')) { $prop(obj, '$$comparable', null); }

    // seal the Object
    Object.seal(obj);

    return obj;
  };

  // freze props, make setters of instance variables throw FrozenError
  Opal.freeze_props = function(obj) {
    var prop, prop_type, desc;

    for(prop in obj) {
      prop_type = typeof(prop);

      // prop_type "object" here is a String(), skip $ props
      if ((prop_type === "string" || prop_type === "object") && prop[0] === '$') {
        continue;
      }

      desc = Object.getOwnPropertyDescriptor(obj, prop);
      if (desc && desc.enumerable && desc.writable) {
        // create closure to retain current value as cv
        // for Opal 2.0 let for cv should do the trick, instead of a function
        (function() {
          // set v to undefined, as if the property is not set
          var cv = obj[prop];
          Object.defineProperty(obj, prop, {
            get: function() { return cv; },
            set: function(_val) { $deny_frozen_access(obj); },
            enumerable: true
          });
        })();
      }
    }
  };

  // Regexps
  // -------

  // Escape Regexp special chars letting the resulting string be used to build
  // a new Regexp.
  //
  Opal.escape_regexp = function(str) {
    return str.replace(/([-[\]\/{}()*+?.^$\\| ])/g, '\\$1')
              .replace(/[\n]/g, '\\n')
              .replace(/[\r]/g, '\\r')
              .replace(/[\f]/g, '\\f')
              .replace(/[\t]/g, '\\t');
  };

  // Create a global Regexp from a RegExp object and cache the result
  // on the object itself ($$g attribute).
  //
  Opal.global_regexp = function(pattern) {
    if (pattern.global) {
      return pattern; // RegExp already has the global flag
    }
    if (pattern.$$g == null) {
      pattern.$$g = new RegExp(pattern.source, (pattern.multiline ? 'gm' : 'g') + (pattern.ignoreCase ? 'i' : ''));
    } else {
      pattern.$$g.lastIndex = null; // reset lastIndex property
    }
    return pattern.$$g;
  };

  // Create a global multiline Regexp from a RegExp object and cache the result
  // on the object itself ($$gm or $$g attribute).
  //
  Opal.global_multiline_regexp = function(pattern) {
    var result, flags;

    // RegExp already has the global and multiline flag
    if (pattern.global && pattern.multiline) return pattern;

    flags = 'gm' + (pattern.ignoreCase ? 'i' : '');
    if (pattern.multiline) {
      // we are using the $$g attribute because the Regexp is already multiline
      if (pattern.$$g == null) {
        pattern.$$g = new RegExp(pattern.source, flags);
      }
      result = pattern.$$g;
    } else {
      if (pattern.$$gm == null) {
        pattern.$$gm = new RegExp(pattern.source, flags);
      }
      result = pattern.$$gm;
    }
    result.lastIndex = null; // reset lastIndex property
    return result;
  };

  // Combine multiple regexp parts together
  Opal.regexp = function(parts, flags) {
    var part;
    var ignoreCase = typeof flags !== 'undefined' && flags && flags.indexOf('i') >= 0;

    for (var i = 0, ii = parts.length; i < ii; i++) {
      part = parts[i];
      if (part instanceof RegExp) {
        if (part.ignoreCase !== ignoreCase)
          Opal.Kernel.$warn(
            "ignore case doesn't match for " + part.source.$inspect(),
            Opal.hash({uplevel: 1})
          );

        part = part.source;
      }
      if (part === '') part = '(?:' + part + ')';
      parts[i] = part;
    }

    if (flags) {
      return new RegExp(parts.join(''), flags);
    } else {
      return new RegExp(parts.join(''));
    }
  };

  // Require system
  // --------------

  Opal.modules         = {};
  Opal.loaded_features = ['corelib/runtime'];
  Opal.current_dir     = '.';
  Opal.require_table   = {'corelib/runtime': true};

  Opal.normalize = function(path) {
    var parts, part, new_parts = [], SEPARATOR = '/';

    if (Opal.current_dir !== '.') {
      path = Opal.current_dir.replace(/\/*$/, '/') + path;
    }

    path = path.replace(/^\.\//, '');
    path = path.replace(/\.(rb|opal|js)$/, '');
    parts = path.split(SEPARATOR);

    for (var i = 0, ii = parts.length; i < ii; i++) {
      part = parts[i];
      if (part === '') continue;
      (part === '..') ? new_parts.pop() : new_parts.push(part);
    }

    return new_parts.join(SEPARATOR);
  };

  Opal.loaded = function(paths) {
    var i, l, path;

    for (i = 0, l = paths.length; i < l; i++) {
      path = Opal.normalize(paths[i]);

      if (Opal.require_table[path]) {
        continue;
      }

      Opal.loaded_features.push(path);
      Opal.require_table[path] = true;
    }
  };

  Opal.load_normalized = function(path) {
    Opal.loaded([path]);

    var module = Opal.modules[path];

    if (module) {
      var retval = module(Opal);
      if (typeof Promise !== 'undefined' && retval instanceof Promise) {
        // A special case of require having an async top:
        // We will need to await it.
        return retval.then($return_val(true));
      }
    }
    else {
      var severity = Opal.config.missing_require_severity;
      var message  = 'cannot load such file -- ' + path;

      if (severity === "error") {
        $raise(Opal.LoadError, message);
      }
      else if (severity === "warning") {
        console.warn('WARNING: LoadError: ' + message);
      }
    }

    return true;
  };

  Opal.load = function(path) {
    path = Opal.normalize(path);

    return Opal.load_normalized(path);
  };

  Opal.require = function(path) {
    path = Opal.normalize(path);

    if (Opal.require_table[path]) {
      return false;
    }

    return Opal.load_normalized(path);
  };


  // Strings
  // -------

  Opal.encodings = Object.create(null);

  // Sets the encoding on a string, will treat string literals as frozen strings
  // raising a FrozenError.
  //
  // @param str [String] the string on which the encoding should be set
  // @param name [String] the canonical name of the encoding
  // @param type [String] possible values are either `"encoding"`, `"internal_encoding"`, or `undefined
  Opal.set_encoding = function(str, name, type) {
    if (typeof type === "undefined") type = "encoding";
    if (typeof str === 'string' || str.$$frozen === true)
      $raise(Opal.FrozenError, "can't modify frozen String");

    var encoding = Opal.find_encoding(name);

    if (encoding === str[type]) { return str; }

    str[type] = encoding;

    return str;
  };

  // Fetches the encoding for the given name or raises ArgumentError.
  Opal.find_encoding = function(name) {
    var register = Opal.encodings;
    var encoding = register[name] || register[name.toUpperCase()];
    if (!encoding) $raise(Opal.ArgumentError, "unknown encoding name - " + name);
    return encoding;
  };

  // @returns a String object with the encoding set from a string literal
  Opal.enc = function(str, name) {
    var dup = new String(str);
    dup = Opal.set_encoding(dup, name);
    dup.internal_encoding = dup.encoding;
    return dup
  };

  // @returns a String object with the internal encoding set to Binary
  Opal.binary = function(str) {
    var dup = new String(str);
    return Opal.set_encoding(dup, "binary", "internal_encoding");
  };

  Opal.last_promise = null;
  Opal.promise_unhandled_exception = false;

  // Run a block of code, but if it returns a Promise, don't run the next
  // one, but queue it.
  Opal.queue = function(proc) {
    if (Opal.last_promise) {
      // The async path is taken only if anything before returned a
      // Promise(V2).
      Opal.last_promise = Opal.last_promise.then(function() {
        if (!Opal.promise_unhandled_exception) return proc(Opal);
      })['catch'](function(error) {
        if (Opal.respond_to(error, '$full_message')) {
          error = error.$full_message();
        }
        console.error(error);
        // Abort further execution
        Opal.promise_unhandled_exception = true;
        Opal.exit(1);
      });
      return Opal.last_promise;
    }
    else {
      var ret = proc(Opal);
      if (typeof Promise === 'function' && typeof ret === 'object' && ret instanceof Promise) {
        Opal.last_promise = ret;
      }
      return ret;
    }
  };

  // Operator helpers
  // ----------------

  function are_both_numbers(l,r) { return typeof(l) === 'number' && typeof(r) === 'number' }

  Opal.rb_plus   = function(l,r) { return are_both_numbers(l,r) ? l + r : l['$+'](r); };
  Opal.rb_minus  = function(l,r) { return are_both_numbers(l,r) ? l - r : l['$-'](r); };
  Opal.rb_times  = function(l,r) { return are_both_numbers(l,r) ? l * r : l['$*'](r); };
  Opal.rb_divide = function(l,r) { return are_both_numbers(l,r) ? l / r : l['$/'](r); };
  Opal.rb_lt     = function(l,r) { return are_both_numbers(l,r) ? l < r : l['$<'](r); };
  Opal.rb_gt     = function(l,r) { return are_both_numbers(l,r) ? l > r : l['$>'](r); };
  Opal.rb_le     = function(l,r) { return are_both_numbers(l,r) ? l <= r : l['$<='](r); };
  Opal.rb_ge     = function(l,r) { return are_both_numbers(l,r) ? l >= r : l['$>='](r); };

  // Optimized helpers for calls like $truthy((a)['$==='](b)) -> $eqeqeq(a, b)
  function are_both_numbers_or_strings(lhs, rhs) {
    return (typeof lhs === 'number' && typeof rhs === 'number') ||
           (typeof lhs === 'string' && typeof rhs === 'string');
  }

  function $eqeq(lhs, rhs) {
    return are_both_numbers_or_strings(lhs,rhs) ? lhs === rhs : $truthy((lhs)['$=='](rhs));
  }  Opal.eqeq = $eqeq;
  Opal.eqeqeq = function(lhs, rhs) {
    return are_both_numbers_or_strings(lhs,rhs) ? lhs === rhs : $truthy((lhs)['$==='](rhs));
  };
  Opal.neqeq = function(lhs, rhs) {
    return are_both_numbers_or_strings(lhs,rhs) ? lhs !== rhs : $truthy((lhs)['$!='](rhs));
  };
  Opal.not = function(arg) {
    if (undefined === arg || null === arg || false === arg || nil === arg) return true;
    if (true === arg || arg['$!'].$$pristine) return false;
    return $truthy(arg['$!']());
  };

  // Shortcuts - optimized function generators for simple kinds of functions
  function $return_val(arg) {
    return function() {
      return arg;
    }
  }
  Opal.return_val = $return_val;

  Opal.return_self = function() {
    return this;
  };
  Opal.return_ivar = function(ivar) {
    return function() {
      if (this[ivar] == null) { return nil; }
      return this[ivar];
    }
  };
  Opal.assign_ivar = function(ivar) {
    return function(val) {
      $deny_frozen_access(this);
      return this[ivar] = val;
    }
  };
  Opal.assign_ivar_val = function(ivar, static_val) {
    return function() {
      $deny_frozen_access(this);
      return this[ivar] = static_val;
    }
  };

  // Primitives for handling parameters
  Opal.ensure_kwargs = function(kwargs) {
    if (kwargs == null) {
      return Opal.hash2([], {});
    } else if (kwargs.$$is_hash) {
      return kwargs;
    } else {
      $raise(Opal.ArgumentError, 'expected kwargs');
    }
  };

  Opal.get_kwarg = function(kwargs, key) {
    if (!$has_own(kwargs.$$smap, key)) {
      $raise(Opal.ArgumentError, 'missing keyword: '+key);
    }
    return kwargs.$$smap[key];
  };

  // Arrays of size > 32 elements that contain only strings,
  // symbols, integers and nils are compiled as a self-extracting
  // string.
  Opal.large_array_unpack = function(str) {
    var array = str.split(","), length = array.length, i;
    for (i = 0; i < length; i++) {
      switch(array[i][0]) {
        case undefined:
          array[i] = nil;
          break;
        case '-':
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
          array[i] = +array[i];
      }
    }
    return array;
  };

  // Initialization
  // --------------
  Opal.BasicObject = BasicObject = $allocate_class('BasicObject', null);
  Opal.Object      = _Object     = $allocate_class('Object', Opal.BasicObject);
  Opal.Module      = Module      = $allocate_class('Module', Opal.Object);
  Opal.Class       = Class       = $allocate_class('Class', Opal.Module);
  Opal.Opal        = _Opal       = $allocate_module('Opal');
  Opal.Kernel      = Kernel      = $allocate_module('Kernel');

  $set_proto(Opal.BasicObject, Opal.Class.$$prototype);
  $set_proto(Opal.Object, Opal.Class.$$prototype);
  $set_proto(Opal.Module, Opal.Class.$$prototype);
  $set_proto(Opal.Class, Opal.Class.$$prototype);

  // BasicObject can reach itself, avoid const_set to skip the $$base_module logic
  BasicObject.$$const.BasicObject = BasicObject;

  // Assign basic constants
  $const_set(_Object, "BasicObject",  BasicObject);
  $const_set(_Object, "Object",       _Object);
  $const_set(_Object, "Module",       Module);
  $const_set(_Object, "Class",        Class);
  $const_set(_Object, "Opal",         _Opal);
  $const_set(_Object, "Kernel",       Kernel);

  // Fix booted classes to have correct .class value
  BasicObject.$$class = Class;
  _Object.$$class     = Class;
  Module.$$class      = Class;
  Class.$$class       = Class;
  _Opal.$$class       = Module;
  Kernel.$$class      = Module;

  // Forward .toString() to #to_s
  $prop(_Object.$$prototype, 'toString', function() {
    var to_s = this.$to_s();
    if (to_s.$$is_string && typeof(to_s) === 'object') {
      // a string created using new String('string')
      return to_s.valueOf();
    } else {
      return to_s;
    }
  });

  // Make Kernel#require immediately available as it's needed to require all the
  // other corelib files.
  $prop(_Object.$$prototype, '$require', Opal.require);

  // Instantiate the main object
  Opal.top = new _Object();
  Opal.top.$to_s = Opal.top.$inspect = $return_val('main');
  Opal.top.$define_method = top_define_method;

  // Foward calls to define_method on the top object to Object
  function top_define_method() {
    var args = $slice(arguments);
    var block = top_define_method.$$p;
    top_define_method.$$p = null;
    return Opal.send(_Object, 'define_method', args, block)
  }
  // Nil
  Opal.NilClass = $allocate_class('NilClass', Opal.Object);
  $const_set(_Object, 'NilClass', Opal.NilClass);
  nil = Opal.nil = new Opal.NilClass();
  nil.$$id = nil_id;
  nil.call = nil.apply = function() { $raise(Opal.LocalJumpError, 'no block given'); };
  nil.$$frozen = true;
  nil.$$comparable = false;
  Object.seal(nil);

  Opal.thrower = function(type) {
    var thrower = new Error('unexpected '+type);
    thrower.$thrower_type = type;
    thrower.$throw = function(value) {
      if (value == null) value = nil;
      thrower.$v = value;
      throw thrower;
    };
    return thrower;
  };

  Opal.t_eval_return = Opal.thrower("return");

  TypeError.$$super = Error;

  // If enable-file-source-embed compiler option is enabled, each module loaded will add its
  // sources to this object
  Opal.file_sources = {};
}).call(undefined);
Opal.loaded(["corelib/runtime.js"]);
Opal.modules["corelib/helpers"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $type_error = Opal.type_error, $coerce_to = Opal.coerce_to, $module = Opal.module, $defs = Opal.defs, $slice = Opal.slice, $eqeqeq = Opal.eqeqeq, $Kernel = Opal.Kernel, $truthy = Opal.truthy, $Opal = Opal.Opal, nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('===,raise,respond_to?,nil?,__send__,<=>,class,coerce_to!,new,to_s,__id__');
  return (function($base) {
    var self = $module($base, 'Opal');

    
    
    $defs(self, '$bridge', function $$bridge(constructor, klass) {
      
      return Opal.bridge(constructor, klass);
    });
    $defs(self, '$coerce_to!', function $Opal_coerce_to$excl$1(object, type, method, $a) {
      var $post_args, args, coerced = nil;

      
      $post_args = $slice(arguments, 3);
      args = $post_args;
      coerced = $coerce_to(object, type, method, args);
      if (!$eqeqeq(type, coerced)) {
        $Kernel.$raise($type_error(object, type, method, coerced));
      }      return coerced;
    }, -4);
    $defs(self, '$coerce_to?', function $Opal_coerce_to$ques$2(object, type, method, $a) {
      var $post_args, args, coerced = nil;

      
      $post_args = $slice(arguments, 3);
      args = $post_args;
      if (!$truthy(object['$respond_to?'](method))) {
        return nil
      }      coerced = $coerce_to(object, type, method, args);
      if ($truthy(coerced['$nil?']())) {
        return nil
      }      if (!$eqeqeq(type, coerced)) {
        $Kernel.$raise($type_error(object, type, method, coerced));
      }      return coerced;
    }, -4);
    $defs(self, '$try_convert', function $$try_convert(object, type, method) {
      
      
      if ($eqeqeq(type, object)) {
        return object
      }      if ($truthy(object['$respond_to?'](method))) {
        return object.$__send__(method)
      } else {
        return nil
      }    });
    $defs(self, '$compare', function $$compare(a, b) {
      var compare = nil;

      
      compare = a['$<=>'](b);
      if ($truthy(compare === nil)) {
        $Kernel.$raise($$$('ArgumentError'), "comparison of " + (a.$class()) + " with " + (b.$class()) + " failed");
      }      return compare;
    });
    $defs(self, '$destructure', function $$destructure(args) {
      
      
      if (args.length == 1) {
        return args[0];
      }
      else if (args.$$is_array) {
        return args;
      }
      else {
        var args_ary = new Array(args.length);
        for(var i = 0, l = args_ary.length; i < l; i++) { args_ary[i] = args[i]; }

        return args_ary;
      }
    
    });
    $defs(self, '$respond_to?', function $Opal_respond_to$ques$3(obj, method, include_all) {
      
      
      if (include_all == null) include_all = false;
      
      if (obj == null || !obj.$$class) {
        return false;
      }
      return obj['$respond_to?'](method, include_all);
    }, -3);
    $defs(self, '$instance_variable_name!', function $Opal_instance_variable_name$excl$4(name) {
      
      
      name = $Opal['$coerce_to!'](name, $$$('String'), "to_str");
      if (!$truthy(/^@[a-zA-Z_][a-zA-Z0-9_]*?$/.test(name))) {
        $Kernel.$raise($$$('NameError').$new("'" + (name) + "' is not allowed as an instance variable name", name));
      }      return name;
    });
    $defs(self, '$class_variable_name!', function $Opal_class_variable_name$excl$5(name) {
      
      
      name = $Opal['$coerce_to!'](name, $$$('String'), "to_str");
      if ($truthy(name.length < 3 || name.slice(0,2) !== '@@')) {
        $Kernel.$raise($$$('NameError').$new("`" + (name) + "' is not allowed as a class variable name", name));
      }      return name;
    });
    $defs(self, '$const_name?', function $Opal_const_name$ques$6(const_name) {
      
      
      if (typeof const_name !== 'string') {
        (const_name = $Opal['$coerce_to!'](const_name, $$$('String'), "to_str"));
      }

      return const_name[0] === const_name[0].toUpperCase()
    
    });
    $defs(self, '$const_name!', function $Opal_const_name$excl$7(const_name) {
      var self = this;

      
      if ($truthy((($$$('::', 'String', 'skip_raise')) ? 'constant' : nil))) {
        const_name = $Opal['$coerce_to!'](const_name, $$$('String'), "to_str");
      }      
      if (!const_name || const_name[0] != const_name[0].toUpperCase()) {
        self.$raise($$$('NameError'), "wrong constant name " + (const_name));
      }
      return const_name;
    });
    $defs(self, '$pristine', function $$pristine(owner_class, $a) {
      var $post_args, method_names;

      
      $post_args = $slice(arguments, 1);
      method_names = $post_args;
      
      var method_name, method;
      for (var i = method_names.length - 1; i >= 0; i--) {
        method_name = method_names[i];
        method = owner_class.$$prototype[Opal.jsid(method_name)];

        if (method && !method.$$stub) {
          method.$$pristine = true;
        }
      }
      return nil;
    }, -2);
    var inspect_stack = [];
    return $defs(self, '$inspect', function $$inspect(value) {
      var e = nil;
      var pushed = false;
      
      return (function() { try {
      try {
        
        
        if (value === null) {
          // JS null value
          return 'null';
        }
        else if (value === undefined) {
          // JS undefined value
          return 'undefined';
        }
        else if (typeof value.$$class === 'undefined') {
          // JS object / other value that is not bridged
          return Object.prototype.toString.apply(value);
        }
        else if (typeof value.$inspect !== 'function' || value.$inspect.$$stub) {
          // BasicObject and friends
          return "#<" + (value.$$class) + ":0x" + (value.$__id__().$to_s(16)) + ">"
        }
        else if (inspect_stack.indexOf(value.$__id__()) !== -1) {
          // inspect recursing inside inspect to find out about the
          // same object
          return "#<" + (value.$$class) + ":0x" + (value.$__id__().$to_s(16)) + ">"
        }
        else {
          // anything supporting Opal
          inspect_stack.push(value.$__id__());
          pushed = true;
          return value.$inspect();
        }
      ;
        return nil;
      } catch ($err) {
        if (Opal.rescue($err, [$$$('Exception')])) {(e = $err);
          try {
            return "#<" + (value.$$class) + ":0x" + (value.$__id__().$to_s(16)) + ">"
          } finally { Opal.pop_exception(); }
        } else { throw $err; }
      }
      } finally {
        if (pushed) inspect_stack.pop();
      } })();    }, -1);
  })('::')
};

Opal.modules["corelib/module"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $truthy = Opal.truthy, $coerce_to = Opal.coerce_to, $const_set = Opal.const_set, $Object = Opal.Object, $return_ivar = Opal.return_ivar, $assign_ivar = Opal.assign_ivar, $ivar = Opal.ivar, $deny_frozen_access = Opal.deny_frozen_access, $freeze = Opal.freeze, $prop = Opal.prop, $jsid = Opal.jsid, $klass = Opal.klass, $defs = Opal.defs, $send = Opal.send, $def = Opal.def, $eqeqeq = Opal.eqeqeq, $Module = Opal.Module, $Kernel = Opal.Kernel, $rb_lt = Opal.rb_lt, $rb_gt = Opal.rb_gt, $slice = Opal.slice, $to_a = Opal.to_a, $hash2 = Opal.hash2, $Opal = Opal.Opal, $return_val = Opal.return_val, $eqeq = Opal.eqeq, $lambda = Opal.lambda, $range = Opal.range, $send2 = Opal.send2, $find_super = Opal.find_super, $alias = Opal.alias, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('module_eval,to_proc,===,raise,equal?,<,>,nil?,attr_reader,attr_writer,warn,attr_accessor,const_name?,class_variable_name!,pristine,const_name!,=~,new,inject,split,const_get,==,start_with?,!~,bind,call,class,frozen?,name,append_features,included,cover?,size,merge,compile,proc,any?,prepend_features,prepended,to_s,__id__,constants,include?,copy_class_variables,copy_constants,class_exec,module_exec,inspect');
  
  (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Module');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting);

    
    $defs(self, '$allocate', function $$allocate() {
      var self = this;

      
      var module = Opal.allocate_module(nil, function(){});
      // Link the prototype of Module subclasses
      if (self !== Opal.Module) Object.setPrototypeOf(module, self.$$prototype);
      return module;
    
    });
    
    $def(self, '$initialize', function $$initialize() {
      var block = $$initialize.$$p || nil, self = this;

      $$initialize.$$p = null;
      if ((block !== nil)) {
        return $send(self, 'module_eval', [], block.$to_proc())
      } else {
        return nil
      }    });
    
    $def(self, '$===', function $Module_$eq_eq_eq$1(object) {
      var self = this;

      
      if ($truthy(object == null)) {
        return false
      }      return Opal.is_a(object, self);    });
    
    $def(self, '$<', function $Module_$lt$2(other) {
      var self = this;

      
      if (!$eqeqeq($Module, other)) {
        $Kernel.$raise($$$('TypeError'), "compared with non class/module");
      }      
      var working = self,
          ancestors,
          i, length;

      if (working === other) {
        return false;
      }

      for (i = 0, ancestors = Opal.ancestors(self), length = ancestors.length; i < length; i++) {
        if (ancestors[i] === other) {
          return true;
        }
      }

      for (i = 0, ancestors = Opal.ancestors(other), length = ancestors.length; i < length; i++) {
        if (ancestors[i] === self) {
          return false;
        }
      }

      return nil;
    });
    
    $def(self, '$<=', function $Module_$lt_eq$3(other) {
      var self = this, $ret_or_1 = nil;

      if ($truthy(($ret_or_1 = self['$equal?'](other)))) {
        return $ret_or_1
      } else {
        return $rb_lt(self, other)
      }
    });
    
    $def(self, '$>', function $Module_$gt$4(other) {
      var self = this;

      
      if (!$eqeqeq($Module, other)) {
        $Kernel.$raise($$$('TypeError'), "compared with non class/module");
      }      return $rb_lt(other, self);
    });
    
    $def(self, '$>=', function $Module_$gt_eq$5(other) {
      var self = this, $ret_or_1 = nil;

      if ($truthy(($ret_or_1 = self['$equal?'](other)))) {
        return $ret_or_1
      } else {
        return $rb_gt(self, other)
      }
    });
    
    $def(self, '$<=>', function $Module_$lt_eq_gt$6(other) {
      var self = this, lt = nil;

      
      
      if (self === other) {
        return 0;
      }
      if (!$eqeqeq($Module, other)) {
        return nil
      }      lt = $rb_lt(self, other);
      if ($truthy(lt['$nil?']())) {
        return nil
      }      if ($truthy(lt)) {
        return -1
      } else {
        return 1
      }    });
    
    $def(self, '$alias_method', function $$alias_method(newname, oldname) {
      var self = this;

      
      $deny_frozen_access(self);
      newname = $coerce_to(newname, $$$('String'), 'to_str');
      oldname = $coerce_to(oldname, $$$('String'), 'to_str');
      Opal.alias(self, newname, oldname);
      return self;
    });
    
    $def(self, '$alias_native', function $$alias_native(mid, jsid) {
      var self = this;

      
      if (jsid == null) jsid = mid;
      $deny_frozen_access(self);
      Opal.alias_native(self, mid, jsid);
      return self;
    }, -2);
    
    $def(self, '$ancestors', function $$ancestors() {
      var self = this;

      return Opal.ancestors(self);
    });
    
    $def(self, '$append_features', function $$append_features(includer) {
      var self = this;

      
      $deny_frozen_access(includer);
      Opal.append_features(self, includer);
      return self;
    });
    
    $def(self, '$attr_accessor', function $$attr_accessor($a) {
      var $post_args, names, self = this;

      
      $post_args = $slice(arguments);
      names = $post_args;
      $send(self, 'attr_reader', $to_a(names));
      return $send(self, 'attr_writer', $to_a(names));
    }, -1);
    
    $def(self, '$attr', function $$attr($a) {
      var $post_args, args, self = this;

      
      $post_args = $slice(arguments);
      args = $post_args;
      
      if (args.length == 2 && (args[1] === true || args[1] === false)) {
        self.$warn("optional boolean argument is obsoleted", $hash2(["uplevel"], {"uplevel": 1}));

        args[1] ? self.$attr_accessor(args[0]) : self.$attr_reader(args[0]);
        return nil;
      }
      return $send(self, 'attr_reader', $to_a(args));
    }, -1);
    
    $def(self, '$attr_reader', function $$attr_reader($a) {
      var $post_args, names, self = this;

      
      $post_args = $slice(arguments);
      names = $post_args;
      
      $deny_frozen_access(self);

      var proto = self.$$prototype;

      for (var i = names.length - 1; i >= 0; i--) {
        var name = names[i],
            id   = $jsid(name),
            ivar = $ivar(name);

        var body = $return_ivar(ivar);

        // initialize the instance variable as nil
        Opal.prop(proto, ivar, nil);

        body.$$parameters = [];
        body.$$arity = 0;

        Opal.defn(self, id, body);
      }
      return nil;
    }, -1);
    
    $def(self, '$attr_writer', function $$attr_writer($a) {
      var $post_args, names, self = this;

      
      $post_args = $slice(arguments);
      names = $post_args;
      
      $deny_frozen_access(self);

      var proto = self.$$prototype;

      for (var i = names.length - 1; i >= 0; i--) {
        var name = names[i],
            id   = $jsid(name + '='),
            ivar = $ivar(name);

        var body = $assign_ivar(ivar);

        body.$$parameters = [['req']];
        body.$$arity = 1;

        // initialize the instance variable as nil
        Opal.prop(proto, ivar, nil);

        Opal.defn(self, id, body);
      }
      return nil;
    }, -1);
    
    $def(self, '$autoload', function $$autoload(const$, path) {
      var self = this;

      
      $deny_frozen_access(self);

      if (!$$('Opal')['$const_name?'](const$)) {
        $Kernel.$raise($$$('NameError'), "autoload must be constant name: " + (const$));
      }

      if (path == "") {
        $Kernel.$raise($$$('ArgumentError'), "empty file name");
      }

      if (!self.$$const.hasOwnProperty(const$)) {
        if (!self.$$autoload) {
          self.$$autoload = {};
        }
        Opal.const_cache_version++;
        self.$$autoload[const$] = { path: path, loaded: false, required: false, success: false, exception: false };

        if (self.$const_added && !self.$const_added.$$pristine) {
          self.$const_added(const$);
        }
      }
      return nil;
    
    });
    
    $def(self, '$autoload?', function $Module_autoload$ques$7(const$) {
      var self = this;

      
      if (self.$$autoload && self.$$autoload[const$] && !self.$$autoload[const$].required && !self.$$autoload[const$].success) {
        return self.$$autoload[const$].path;
      }

      var ancestors = self.$ancestors();

      for (var i = 0, length = ancestors.length; i < length; i++) {
        if (ancestors[i].$$autoload && ancestors[i].$$autoload[const$] && !ancestors[i].$$autoload[const$].required && !ancestors[i].$$autoload[const$].success) {
          return ancestors[i].$$autoload[const$].path;
        }
      }
      return nil;
    
    });
    
    $def(self, '$class_variables', function $$class_variables() {
      var self = this;

      return Object.keys(Opal.class_variables(self));
    });
    
    $def(self, '$class_variable_get', function $$class_variable_get(name) {
      var self = this;

      
      name = $Opal['$class_variable_name!'](name);
      return Opal.class_variable_get(self, name, false);    });
    
    $def(self, '$class_variable_set', function $$class_variable_set(name, value) {
      var self = this;

      
      $deny_frozen_access(self);
      name = $Opal['$class_variable_name!'](name);
      return Opal.class_variable_set(self, name, value);    });
    
    $def(self, '$class_variable_defined?', function $Module_class_variable_defined$ques$8(name) {
      var self = this;

      
      name = $Opal['$class_variable_name!'](name);
      return Opal.class_variables(self).hasOwnProperty(name);    });
    
    $def(self, '$const_added', $return_val(nil));
    $Opal.$pristine(self, "const_added");
    
    $def(self, '$remove_class_variable', function $$remove_class_variable(name) {
      var self = this;

      
      $deny_frozen_access(self);
      name = $Opal['$class_variable_name!'](name);
      
      if (Opal.hasOwnProperty.call(self.$$cvars, name)) {
        var value = self.$$cvars[name];
        delete self.$$cvars[name];
        return value;
      } else {
        $Kernel.$raise($$$('NameError'), "cannot remove " + (name) + " for " + (self));
      }
    });
    
    $def(self, '$constants', function $$constants(inherit) {
      var self = this;

      
      if (inherit == null) inherit = true;
      return Opal.constants(self, inherit);    }, -1);
    $defs(self, '$constants', function $$constants(inherit) {
      var self = this;
      
      if (inherit == null) {
        var nesting = (self.$$nesting || []).concat($Object),
            constant, constants = {},
            i, ii;

        for(i = 0, ii = nesting.length; i < ii; i++) {
          for (constant in nesting[i].$$const) {
            constants[constant] = true;
          }
        }
        return Object.keys(constants);
      } else {
        return Opal.constants(self, inherit)
      }
    }, -1);
    $defs(self, '$nesting', function $$nesting() {
      var self = this;

      return self.$$nesting || [];
    });
    
    $def(self, '$const_defined?', function $Module_const_defined$ques$9(name, inherit) {
      var self = this;

      
      if (inherit == null) inherit = true;
      name = $$('Opal')['$const_name!'](name);
      if (!$truthy(name['$=~']($$$($Opal, 'CONST_NAME_REGEXP')))) {
        $Kernel.$raise($$$('NameError').$new("wrong constant name " + (name), name));
      }      
      var module, modules = [self], i, ii;

      // Add up ancestors if inherit is true
      if (inherit) {
        modules = modules.concat(Opal.ancestors(self));

        // Add Object's ancestors if it's a module – modules have no ancestors otherwise
        if (self.$$is_module) {
          modules = modules.concat([$Object]).concat(Opal.ancestors($Object));
        }
      }

      for (i = 0, ii = modules.length; i < ii; i++) {
        module = modules[i];
        if (module.$$const[name] != null) { return true; }
        if (
          module.$$autoload &&
          module.$$autoload[name] &&
          !module.$$autoload[name].required &&
          !module.$$autoload[name].success
        ) {
          return true;
        }
      }

      return false;
    }, -2);
    
    $def(self, '$const_get', function $$const_get(name, inherit) {
      var self = this;

      
      if (inherit == null) inherit = true;
      name = $$('Opal')['$const_name!'](name);
      
      if (name.indexOf('::') === 0 && name !== '::'){
        name = name.slice(2);
      }
      if ($truthy(name.indexOf('::') != -1 && name != '::')) {
        return $send(name.$split("::"), 'inject', [self], function $$10(o, c){
          
          if (o == null) o = nil;
          if (c == null) c = nil;
          return o.$const_get(c);})
      }      if (!$truthy(name['$=~']($$$($Opal, 'CONST_NAME_REGEXP')))) {
        $Kernel.$raise($$$('NameError').$new("wrong constant name " + (name), name));
      }      
      if (inherit) {
        return Opal.$$([self], name);
      } else {
        return Opal.const_get_local(self, name);
      }
    }, -2);
    
    $def(self, '$const_missing', function $$const_missing(name) {
      var self = this, full_const_name = nil;

      
      full_const_name = ($eqeq(self, $Object) ? (name) : ("" + (self) + "::" + (name)));
      return $Kernel.$raise($$$('NameError').$new("uninitialized constant " + (full_const_name), name));
    });
    
    $def(self, '$const_set', function $$const_set(name, value) {
      var self = this;

      
      $deny_frozen_access(self);
      name = $Opal['$const_name!'](name);
      if (($truthy(name['$!~']($$$($Opal, 'CONST_NAME_REGEXP'))) || ($truthy(name['$start_with?']("::"))))) {
        $Kernel.$raise($$$('NameError').$new("wrong constant name " + (name), name));
      }      $const_set(self, name, value);
      return value;
    });
    
    $def(self, '$public_constant', $return_val(nil));
    
    $def(self, '$define_method', function $$define_method(name, method) {
      var block = $$define_method.$$p || nil, self = this, $ret_or_1 = nil, $ret_or_2 = nil;

      $$define_method.$$p = null;
      
      $deny_frozen_access(self);

      if (method === undefined && block === nil)
        $Kernel.$raise($$$('ArgumentError'), "tried to create a Proc object without a block")
    ;
      block = ($truthy(($ret_or_1 = block)) ? ($ret_or_1) : ($eqeqeq($$$('Proc'), ($ret_or_2 = method)) ? (method) : ($eqeqeq($$$('Method'), $ret_or_2) ? (method.$to_proc().$$unbound) : ($eqeqeq($$$('UnboundMethod'), $ret_or_2) ? ($lambda(function $$11($a){var $post_args, args, self = $$11.$$s == null ? this : $$11.$$s, bound = nil;

        
        $post_args = $slice(arguments);
        args = $post_args;
        bound = method.$bind(self);
        return $send(bound, 'call', $to_a(args));}, {$$arity: -1, $$s: self})) : ($Kernel.$raise($$$('TypeError'), "wrong argument type " + (block.$class()) + " (expected Proc/Method)"))))));
      
      if (typeof(Proxy) !== 'undefined') {

        block.$$proxy_target = block;
        block = new Proxy(block, {
          apply: function(target, self, args) {
            var old_name = target.$$jsid;
            target.$$jsid = name;
            try {
              return target.apply(self, args);
            } catch(e) {
              if (e === target.$$brk || e === target.$$ret) return e.$v;
              throw e;
            } finally {
              target.$$jsid = old_name;
            }
          }
        });
      }

      block.$$jsid        = name;
      block.$$s           = null;
      block.$$def         = block;
      block.$$define_meth = true;

      return Opal.defn(self, $jsid(name), block);
    }, -2);
    
    $def(self, '$freeze', function $$freeze() {
      var self = this;

      
      if ($truthy(self['$frozen?']())) {
        return self
      }      
      if (!self.hasOwnProperty('$$base_module')) { $prop(self, '$$base_module', null); }

      return $freeze(self);
    });
    
    $def(self, '$remove_method', function $$remove_method($a) {
      var $post_args, names, self = this;

      
      $post_args = $slice(arguments);
      names = $post_args;
      
      for (var i = 0; i < names.length; i++) {
        var name = names[i];
        if (!(typeof name === "string" || name.$$is_string)) {
          self.$raise($$$('TypeError'), "" + (self.$name()) + " is not a symbol nor a string");
        }
        $deny_frozen_access(self);

        Opal.rdef(self, "$" + name);
      }
      return self;
    }, -1);
    
    $def(self, '$singleton_class?', function $Module_singleton_class$ques$12() {
      var self = this;

      return !!self.$$is_singleton;
    });
    
    $def(self, '$include', function $$include($a) {
      var $post_args, mods, self = this;

      
      $post_args = $slice(arguments);
      mods = $post_args;
      
      for (var i = mods.length - 1; i >= 0; i--) {
        var mod = mods[i];

        if (!mod.$$is_module) {
          $Kernel.$raise($$$('TypeError'), "wrong argument type " + ((mod).$class()) + " (expected Module)");
        }

        (mod).$append_features(self);
        (mod).$included(self);
      }
      return self;
    }, -1);
    
    $def(self, '$included_modules', function $$included_modules() {
      var self = this;

      return Opal.included_modules(self);
    });
    
    $def(self, '$include?', function $Module_include$ques$13(mod) {
      var self = this;

      
      if (!mod.$$is_module) {
        $Kernel.$raise($$$('TypeError'), "wrong argument type " + ((mod).$class()) + " (expected Module)");
      }

      var i, ii, mod2, ancestors = Opal.ancestors(self);

      for (i = 0, ii = ancestors.length; i < ii; i++) {
        mod2 = ancestors[i];
        if (mod2 === mod && mod2 !== self) {
          return true;
        }
      }

      return false;
    
    });
    
    $def(self, '$instance_method', function $$instance_method(name) {
      var self = this;

      
      var meth = self.$$prototype[$jsid(name)];

      if (!meth || meth.$$stub) {
        $Kernel.$raise($$$('NameError').$new("undefined method `" + (name) + "' for class `" + (self.$name()) + "'", name));
      }

      return $$$('UnboundMethod').$new(self, meth.$$owner || self, meth, name);
    
    });
    
    $def(self, '$instance_methods', function $$instance_methods(include_super) {
      var self = this;

      
      if (include_super == null) include_super = true;
      
      if ($truthy(include_super)) {
        return Opal.instance_methods(self);
      } else {
        return Opal.own_instance_methods(self);
      }
    }, -1);
    
    $def(self, '$included', $return_val(nil));
    
    $def(self, '$extended', $return_val(nil));
    
    $def(self, '$extend_object', function $$extend_object(object) {
      
      
      $deny_frozen_access(object);
      return nil;
    });
    
    $def(self, '$method_added', function $$method_added($a) {

      
      $slice(arguments);
      return nil;
    }, -1);
    
    $def(self, '$method_removed', function $$method_removed($a) {

      
      $slice(arguments);
      return nil;
    }, -1);
    
    $def(self, '$method_undefined', function $$method_undefined($a) {

      
      $slice(arguments);
      return nil;
    }, -1);
    
    $def(self, '$module_eval', function $$module_eval($a) {
      var block = $$module_eval.$$p || nil, $post_args, args, $b, self = this, string = nil, file = nil, default_eval_options = nil, $ret_or_1 = nil, compiling_options = nil, compiled = nil;

      $$module_eval.$$p = null;
      $post_args = $slice(arguments);
      args = $post_args;
      if (($truthy(block['$nil?']()) && ($truthy(!!Opal.compile)))) {
        
        if (!$truthy($range(1, 3, false)['$cover?'](args.$size()))) {
          $Kernel.$raise($$$('ArgumentError'), "wrong number of arguments (0 for 1..3)");
        }        $b = [].concat($to_a(args)), (string = ($b[0] == null ? nil : $b[0])), (file = ($b[1] == null ? nil : $b[1])), (($b[2] == null ? nil : $b[2]));
        default_eval_options = $hash2(["file", "eval"], {"file": ($truthy(($ret_or_1 = file)) ? ($ret_or_1) : ("(eval)")), "eval": true});
        compiling_options = Opal.hash({ arity_check: false }).$merge(default_eval_options);
        compiled = $Opal.$compile(string, compiling_options);
        block = $send($Kernel, 'proc', [], function $$14(){var self = $$14.$$s == null ? this : $$14.$$s;

          return new Function("Opal,self", "return " + compiled)(Opal, self);}, {$$s: self});
      } else if ($truthy(args['$any?']())) {
        $Kernel.$raise($$$('ArgumentError'), "" + ("wrong number of arguments (" + (args.$size()) + " for 0)") + "\n\n  NOTE:If you want to enable passing a String argument please add \"require 'opal-parser'\" to your script\n");
      }      
      var old = block.$$s,
          result;

      block.$$s = null;
      result = block.apply(self, [self]);
      block.$$s = old;

      return result;
    }, -1);
    
    $def(self, '$module_exec', function $$module_exec($a) {
      var block = $$module_exec.$$p || nil, $post_args, args, self = this;

      $$module_exec.$$p = null;
      $post_args = $slice(arguments);
      args = $post_args;
      
      if (block === nil) {
        $Kernel.$raise($$$('LocalJumpError'), "no block given");
      }

      var block_self = block.$$s, result;

      block.$$s = null;
      result = block.apply(self, args);
      block.$$s = block_self;

      return result;
    }, -1);
    
    $def(self, '$method_defined?', function $Module_method_defined$ques$15(method) {
      var self = this;

      
      var body = self.$$prototype[$jsid(method)];
      return (!!body) && !body.$$stub;
    
    });
    
    $def(self, '$module_function', function $$module_function($a) {
      var $post_args, methods, self = this;

      
      $post_args = $slice(arguments);
      methods = $post_args;
      
      $deny_frozen_access(self);

      if (methods.length === 0) {
        self.$$module_function = true;
        return nil;
      }
      else {
        for (var i = 0, length = methods.length; i < length; i++) {
          var meth = methods[i],
              id   = $jsid(meth),
              func = self.$$prototype[id];

          Opal.defs(self, id, func);
        }
        return methods.length === 1 ? methods[0] : methods;
      }
    }, -1);
    
    $def(self, '$name', function $$name() {
      var self = this;

      
      if (self.$$full_name) {
        return self.$$full_name;
      }

      var result = [], base = self;

      while (base) {
        // Give up if any of the ancestors is unnamed
        if (base.$$name === nil || base.$$name == null) return nil;

        result.unshift(base.$$name);

        base = base.$$base_module;

        if (base === $Object) {
          break;
        }
      }

      if (result.length === 0) {
        return nil;
      }

      return self.$$full_name = result.join('::');
    
    });
    
    $def(self, '$prepend', function $$prepend($a) {
      var $post_args, mods, self = this;

      
      $post_args = $slice(arguments);
      mods = $post_args;
      
      if (mods.length === 0) {
        $Kernel.$raise($$$('ArgumentError'), "wrong number of arguments (given 0, expected 1+)");
      }

      for (var i = mods.length - 1; i >= 0; i--) {
        var mod = mods[i];

        if (!mod.$$is_module) {
          $Kernel.$raise($$$('TypeError'), "wrong argument type " + ((mod).$class()) + " (expected Module)");
        }

        (mod).$prepend_features(self);
        (mod).$prepended(self);
      }
      return self;
    }, -1);
    
    $def(self, '$prepend_features', function $$prepend_features(prepender) {
      var self = this;

      
      
      $deny_frozen_access(prepender);

      if (!self.$$is_module) {
        $Kernel.$raise($$$('TypeError'), "wrong argument type " + (self.$class()) + " (expected Module)");
      }

      Opal.prepend_features(self, prepender)
    ;
      return self;
    });
    
    $def(self, '$prepended', $return_val(nil));
    
    $def(self, '$remove_const', function $$remove_const(name) {
      var self = this;

      
      $deny_frozen_access(self);
      return Opal.const_remove(self, name);    });
    
    $def(self, '$to_s', function $$to_s() {
      var self = this, $ret_or_1 = nil;

      if ($truthy(($ret_or_1 = Opal.Module.$name.call(self)))) {
        return $ret_or_1
      } else {
        return "#<" + (self.$$is_module ? 'Module' : 'Class') + ":0x" + (self.$__id__().$to_s(16)) + ">"
      }
    });
    
    $def(self, '$undef_method', function $$undef_method($a) {
      var $post_args, names, self = this;

      
      $post_args = $slice(arguments);
      names = $post_args;
      
      for (var i = 0; i < names.length; i++) {
        var name = names[i];
        if (!(typeof name === "string" || name.$$is_string)) {
          self.$raise($$$('TypeError'), "" + (self.$name()) + " is not a symbol nor a string");
        }
        $deny_frozen_access(self);

        Opal.udef(self, "$" + names[i]);
      }
      return self;
    }, -1);
    
    $def(self, '$instance_variables', function $$instance_variables() {
      var self = this, consts = nil;

      
      consts = (Opal.Module.$$nesting = $nesting, self.$constants());
      
      var result = [];

      for (var name in self) {
        if (self.hasOwnProperty(name) && name.charAt(0) !== '$' && name !== 'constructor' && !consts['$include?'](name)) {
          result.push('@' + name);
        }
      }

      return result;
    });
    
    $def(self, '$dup', function $$dup() {
      var $yield = $$dup.$$p || nil, self = this, copy = nil;

      $$dup.$$p = null;
      
      copy = $send2(self, $find_super(self, 'dup', $$dup, false, true), 'dup', [], $yield);
      copy.$copy_class_variables(self);
      copy.$copy_constants(self);
      return copy;
    });
    
    $def(self, '$copy_class_variables', function $$copy_class_variables(other) {
      var self = this;

      
      for (var name in other.$$cvars) {
        self.$$cvars[name] = other.$$cvars[name];
      }
    
    });
    
    $def(self, '$copy_constants', function $$copy_constants(other) {
      var self = this;

      
      var name, other_constants = other.$$const;

      for (name in other_constants) {
        $const_set(self, name, other_constants[name]);
      }
    
    });
    
    $def(self, '$refine', function $$refine(klass) {
      var block = $$refine.$$p || nil, $a, self = this, refinement_module = nil, m = nil, klass_id = nil;

      $$refine.$$p = null;
      $a = [self, nil, nil], (refinement_module = $a[0]), (m = $a[1]), (klass_id = $a[2]);
      
      klass_id = Opal.id(klass);
      if (typeof self.$$refine_modules === "undefined") {
        self.$$refine_modules = Object.create(null);
      }
      if (typeof self.$$refine_modules[klass_id] === "undefined") {
        m = self.$$refine_modules[klass_id] = $$$('Refinement').$new();
      }
      else {
        m = self.$$refine_modules[klass_id];
      }
      m.refinement_module = refinement_module;
      m.refined_class = klass
    ;
      $send(m, 'class_exec', [], block.$to_proc());
      return m;
    });
    
    $def(self, '$refinements', function $$refinements() {
      var self = this;

      
      var refine_modules = self.$$refine_modules, hash = $hash2([], {});      if (typeof refine_modules === "undefined") return hash;
      for (var id in refine_modules) {
        hash['$[]='](refine_modules[id].refined_class, refine_modules[id]);
      }
      return hash;
    
    });
    
    $def(self, '$using', function $$using(mod) {
      
      return $Kernel.$raise("Module#using is not permitted in methods")
    });
    $alias(self, "class_eval", "module_eval");
    $alias(self, "class_exec", "module_exec");
    return $alias(self, "inspect", "to_s");
  })('::', null, $nesting);
  return (function($base, $super) {
    var self = $klass($base, $super, 'Refinement');

    var $proto = self.$$prototype;

    $proto.refinement_module = $proto.refined_class = nil;
    
    self.$attr_reader("refined_class");
    return $def(self, '$inspect', function $$inspect() {
      var $yield = $$inspect.$$p || nil, self = this;

      $$inspect.$$p = null;
      if ($truthy(self.refinement_module)) {
        return "#<refinement:" + (self.refined_class.$inspect()) + "@" + (self.refinement_module.$inspect()) + ">"
      } else {
        return $send2(self, $find_super(self, 'inspect', $$inspect, false, true), 'inspect', [], $yield)
      }
    });
  })('::', $Module);
};

Opal.modules["corelib/class"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $send = Opal.send, $defs = Opal.defs, $def = Opal.def, $rb_plus = Opal.rb_plus, $return_val = Opal.return_val, $slice = Opal.slice, $send2 = Opal.send2, $find_super = Opal.find_super, $Kernel = Opal.Kernel, $alias = Opal.alias, self = Opal.top, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('require,class_eval,to_proc,+,subclasses,flatten,map,initialize_copy,allocate,name,to_s,raise');
  
  self.$require("corelib/module");
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Class');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting);

    
    $defs(self, '$new', function $Class_new$1(superclass) {
      var block = $Class_new$1.$$p || nil;

      $Class_new$1.$$p = null;
      if (superclass == null) superclass = $$('Object');
      
      if (!superclass.$$is_class) {
        throw Opal.TypeError.$new("superclass must be a Class");
      }

      var klass = Opal.allocate_class(nil, superclass);
      superclass.$inherited(klass);
      ((block !== nil) ? ($send((klass), 'class_eval', [], block.$to_proc())) : nil);
      return klass;
    }, -1);
    
    $def(self, '$allocate', function $$allocate() {
      var self = this;

      
      var obj = new self.$$constructor();
      obj.$$id = Opal.uid();
      return obj;
    
    });
    
    $def(self, '$descendants', function $$descendants() {
      var self = this;

      return $rb_plus(self.$subclasses(), $send(self.$subclasses(), 'map', [], "descendants".$to_proc()).$flatten())
    });
    
    $def(self, '$inherited', $return_val(nil));
    
    $def(self, '$initialize_dup', function $$initialize_dup(original) {
      var self = this;

      
      self.$initialize_copy(original);
      
      self.$$name = null;
      self.$$full_name = null;
    });
    
    $def(self, '$new', function $Class_new$2($a) {
      var block = $Class_new$2.$$p || nil, $post_args, args, self = this;

      $Class_new$2.$$p = null;
      $post_args = $slice(arguments);
      args = $post_args;
      
      var object = self.$allocate();
      Opal.send(object, object.$initialize, args, block);
      return object;
    }, -1);
    
    $def(self, '$subclasses', function $$subclasses() {
      var self = this;

      
      if (typeof WeakRef !== 'undefined') {
        var i, subclass, out = [];
        for (i = 0; i < self.$$subclasses.length; i++) {
          subclass = self.$$subclasses[i].deref();
          if (subclass !== undefined) {
            out.push(subclass);
          }
        }
        return out;
      }
      else {
        return self.$$subclasses;
      }
    
    });
    
    $def(self, '$superclass', function $$superclass() {
      var self = this;

      return self.$$super || nil;
    });
    
    $def(self, '$to_s', function $$to_s() {
      var self = this;

      $$to_s.$$p = null;
      
      var singleton_of = self.$$singleton_of;

      if (singleton_of && singleton_of.$$is_a_module) {
        return "#<Class:" + ((singleton_of).$name()) + ">";
      }
      else if (singleton_of) {
        // a singleton class created from an object
        return "#<Class:#<" + ((singleton_of.$$class).$name()) + ":0x" + ((Opal.id(singleton_of)).$to_s(16)) + ">>";
      }

      return $send2(self, $find_super(self, 'to_s', $$to_s, false, true), 'to_s', [], null);
    
    });
    
    $def(self, '$attached_object', function $$attached_object() {
      var self = this;

      
      if (self.$$singleton_of != null) {
        return self.$$singleton_of;
      }
      else {
        $Kernel.$raise($$$('TypeError'), "`" + (self) + "' is not a singleton class");
      }
    
    });
    return $alias(self, "inspect", "to_s");
  })('::', null, $nesting);
};

Opal.modules["corelib/basic_object"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $slice = Opal.slice, $def = Opal.def, $alias = Opal.alias, $return_val = Opal.return_val, $Opal = Opal.Opal, $truthy = Opal.truthy, $range = Opal.range, $Kernel = Opal.Kernel, $to_a = Opal.to_a, $hash2 = Opal.hash2, $send = Opal.send, $eqeq = Opal.eqeq, $rb_ge = Opal.rb_ge, nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('==,raise,inspect,pristine,!,nil?,cover?,size,merge,compile,proc,[],first,>=,length,instance_variable_get,any?,new,caller');
  return (function($base, $super) {
    var self = $klass($base, $super, 'BasicObject');

    
    
    
    $def(self, '$initialize', function $$initialize($a) {

      
      $slice(arguments);
      return nil;
    }, -1);
    
    $def(self, '$==', function $BasicObject_$eq_eq$1(other) {
      var self = this;

      return self === other;
    });
    
    $def(self, '$eql?', function $BasicObject_eql$ques$2(other) {
      var self = this;

      return self['$=='](other)
    });
    $alias(self, "equal?", "==");
    
    $def(self, '$__id__', function $$__id__() {
      var self = this;

      
      if (self.$$id != null) {
        return self.$$id;
      }
      Opal.prop(self, '$$id', Opal.uid());
      return self.$$id;
    
    });
    
    $def(self, '$__send__', function $$__send__(symbol, $a) {
      var block = $$__send__.$$p || nil, $post_args, args, self = this;

      $$__send__.$$p = null;
      $post_args = $slice(arguments, 1);
      args = $post_args;
      
      if (!symbol.$$is_string) {
        self.$raise($$$('TypeError'), "" + (self.$inspect()) + " is not a symbol nor a string");
      }

      var func = self[Opal.jsid(symbol)];

      if (func) {
        if (block !== nil) {
          func.$$p = block;
        }

        return func.apply(self, args);
      }

      if (block !== nil) {
        self.$method_missing.$$p = block;
      }

      return self.$method_missing.apply(self, [symbol].concat(args));
    }, -2);
    
    $def(self, '$!', $return_val(false));
    $Opal.$pristine("!");
    
    $def(self, '$!=', function $BasicObject_$not_eq$3(other) {
      var self = this;

      return self['$=='](other)['$!']()
    });
    
    $def(self, '$instance_eval', function $$instance_eval($a) {
      var block = $$instance_eval.$$p || nil, $post_args, args, $b, self = this, string = nil, file = nil, default_eval_options = nil, $ret_or_1 = nil, compiling_options = nil, compiled = nil;

      $$instance_eval.$$p = null;
      $post_args = $slice(arguments);
      args = $post_args;
      if (($truthy(block['$nil?']()) && ($truthy(!!Opal.compile)))) {
        
        if (!$truthy($range(1, 3, false)['$cover?'](args.$size()))) {
          $Kernel.$raise($$$('ArgumentError'), "wrong number of arguments (0 for 1..3)");
        }        $b = [].concat($to_a(args)), (string = ($b[0] == null ? nil : $b[0])), (file = ($b[1] == null ? nil : $b[1])), (($b[2] == null ? nil : $b[2]));
        default_eval_options = $hash2(["file", "eval"], {"file": ($truthy(($ret_or_1 = file)) ? ($ret_or_1) : ("(eval)")), "eval": true});
        compiling_options = Opal.hash({ arity_check: false }).$merge(default_eval_options);
        compiled = $Opal.$compile(string, compiling_options);
        block = $send($Kernel, 'proc', [], function $$4(){var self = $$4.$$s == null ? this : $$4.$$s;

          return new Function("Opal,self", "return " + compiled)(Opal, self);}, {$$s: self});
      } else if ((($truthy(block['$nil?']()) && ($truthy($rb_ge(args.$length(), 1)))) && ($eqeq(args.$first()['$[]'](0), "@")))) {
        return self.$instance_variable_get(args.$first())
      } else if ($truthy(args['$any?']())) {
        $Kernel.$raise($$$('ArgumentError'), "wrong number of arguments (" + (args.$size()) + " for 0)");
      }      
      var old = block.$$s,
          result;

      block.$$s = null;

      // Need to pass $$eval so that method definitions know if this is
      // being done on a class/module. Cannot be compiler driven since
      // send(:instance_eval) needs to work.
      if (self.$$is_a_module) {
        self.$$eval = true;
        try {
          result = block.call(self, self);
        }
        finally {
          self.$$eval = false;
        }
      }
      else {
        result = block.call(self, self);
      }

      block.$$s = old;

      return result;
    }, -1);
    
    $def(self, '$instance_exec', function $$instance_exec($a) {
      var block = $$instance_exec.$$p || nil, $post_args, args, self = this;

      $$instance_exec.$$p = null;
      $post_args = $slice(arguments);
      args = $post_args;
      if (!$truthy(block)) {
        $Kernel.$raise($$$('ArgumentError'), "no block given");
      }      
      var block_self = block.$$s,
          result;

      block.$$s = null;

      if (self.$$is_a_module) {
        self.$$eval = true;
        try {
          result = block.apply(self, args);
        }
        finally {
          self.$$eval = false;
        }
      }
      else {
        result = block.apply(self, args);
      }

      block.$$s = block_self;

      return result;
    }, -1);
    
    $def(self, '$singleton_method_added', function $$singleton_method_added($a) {

      
      $slice(arguments);
      return nil;
    }, -1);
    
    $def(self, '$singleton_method_removed', function $$singleton_method_removed($a) {

      
      $slice(arguments);
      return nil;
    }, -1);
    
    $def(self, '$singleton_method_undefined', function $$singleton_method_undefined($a) {

      
      $slice(arguments);
      return nil;
    }, -1);
    
    $def(self, '$method_missing', function $$method_missing(symbol, $a) {
      var $post_args, args, self = this, inspect_result = nil;

      $$method_missing.$$p = null;
      $post_args = $slice(arguments, 1);
      args = $post_args;
      inspect_result = $Opal.$inspect(self);
      return $Kernel.$raise($$$('NoMethodError').$new("undefined method `" + (symbol) + "' for " + (inspect_result), symbol, args), nil, $Kernel.$caller(1));
    }, -2);
    $Opal.$pristine(self, "method_missing");
    return $def(self, '$respond_to_missing?', function $BasicObject_respond_to_missing$ques$5(method_name, include_all) {
      return false;
    }, -2);
  })('::', null)
};

Opal.modules["corelib/kernel"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $truthy = Opal.truthy, $coerce_to = Opal.coerce_to, $respond_to = Opal.respond_to, $Opal = Opal.Opal, $deny_frozen_access = Opal.deny_frozen_access, $freeze = Opal.freeze, $freeze_props = Opal.freeze_props, $jsid = Opal.jsid, $module = Opal.module, $return_val = Opal.return_val, $def = Opal.def, $Kernel = Opal.Kernel, $gvars = Opal.gvars, $slice = Opal.slice, $send = Opal.send, $to_a = Opal.to_a, $ensure_kwargs = Opal.ensure_kwargs, $eqeq = Opal.eqeq, $hash2 = Opal.hash2, $rb_plus = Opal.rb_plus, $eqeqeq = Opal.eqeqeq, $return_self = Opal.return_self, $rb_le = Opal.rb_le, $extract_kwargs = Opal.extract_kwargs, $rb_lt = Opal.rb_lt, $Object = Opal.Object, $alias = Opal.alias, $klass = Opal.klass, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('!,=~,==,object_id,raise,new,class,coerce_to?,<<,map,caller,nil?,allocate,copy_instance_variables,copy_singleton_methods,initialize_clone,frozen?,freeze,initialize_copy,define_method,singleton_class,to_proc,initialize_dup,for,empty?,pop,call,append_features,extend_object,extended,gets,__id__,include?,each,instance_variables,instance_variable_get,inspect,+,to_s,instance_variable_name!,respond_to?,to_int,coerce_to!,Integer,===,enum_for,result,any?,print,format,puts,<=,length,[],readline,<,first,split,to_str,exception,backtrace,rand,respond_to_missing?,pristine,try_convert!,expand_path,join,start_with?,new_seed,srand,tag,value,open,is_a?,__send__,yield_self,include');
  
  (function($base, $parent_nesting) {
    var self = $module($base, 'Kernel');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting);

    
    
    $def(self, '$=~', $return_val(false));
    
    $def(self, '$!~', function $Kernel_$excl_tilde$1(obj) {
      var self = this;

      return self['$=~'](obj)['$!']()
    });
    
    $def(self, '$===', function $Kernel_$eq_eq_eq$2(other) {
      var self = this, $ret_or_1 = nil;

      if ($truthy(($ret_or_1 = self.$object_id()['$=='](other.$object_id())))) {
        return $ret_or_1
      } else {
        return self['$=='](other)
      }
    });
    
    $def(self, '$<=>', function $Kernel_$lt_eq_gt$3(other) {
      var self = this;

      
      // set guard for infinite recursion
      self.$$comparable = true;

      var x = self['$=='](other);

      if (x && x !== nil) {
        return 0;
      }

      return nil;
    
    });
    
    $def(self, '$method', function $$method(name) {
      var self = this;

      
      var meth = self[$jsid(name)];

      if (!meth || meth.$$stub) {
        $Kernel.$raise($$$('NameError').$new("undefined method `" + (name) + "' for class `" + (self.$class()) + "'", name));
      }

      return $$$('Method').$new(self, meth.$$owner || self.$class(), meth, name);
    
    });
    
    $def(self, '$methods', function $$methods(all) {
      var self = this;

      
      if (all == null) all = true;
      
      if ($truthy(all)) {
        return Opal.methods(self);
      } else {
        return Opal.own_methods(self);
      }
    }, -1);
    
    $def(self, '$public_methods', function $$public_methods(all) {
      var self = this;

      
      if (all == null) all = true;
      
      if ($truthy(all)) {
        return Opal.methods(self);
      } else {
        return Opal.receiver_methods(self);
      }
    }, -1);
    
    $def(self, '$Array', function $$Array(object) {
      
      
      var coerced;

      if (object === nil) {
        return [];
      }

      if (object.$$is_array) {
        return object;
      }

      coerced = $Opal['$coerce_to?'](object, $$$('Array'), "to_ary");
      if (coerced !== nil) { return coerced; }

      coerced = $Opal['$coerce_to?'](object, $$$('Array'), "to_a");
      if (coerced !== nil) { return coerced; }

      return [object];
    
    });
    
    $def(self, '$at_exit', function $$at_exit() {
      var block = $$at_exit.$$p || nil, $ret_or_1 = nil;
      if ($gvars.__at_exit__ == null) $gvars.__at_exit__ = nil;

      $$at_exit.$$p = null;
      $gvars.__at_exit__ = ($truthy(($ret_or_1 = $gvars.__at_exit__)) ? ($ret_or_1) : ([]));
      $gvars.__at_exit__['$<<'](block);
      return block;
    });
    
    $def(self, '$caller', function $$caller(start, length) {
      
      
      if (start == null) start = 1;
      if (length == null) length = nil;
      
      var stack, result;

      stack = new Error().$backtrace();
      result = [];

      for (var i = start + 1, ii = stack.length; i < ii; i++) {
        if (!stack[i].match(/runtime\.js/)) {
          result.push(stack[i]);
        }
      }
      if (length != nil) result = result.slice(0, length);
      return result;
    }, -1);
    
    $def(self, '$caller_locations', function $$caller_locations($a) {
      var $post_args, args, self = this;

      
      $post_args = $slice(arguments);
      args = $post_args;
      return $send($send(self, 'caller', $to_a(args)), 'map', [], function $$4(loc){
        
        if (loc == null) loc = nil;
        return $$$($$$($$$('Thread'), 'Backtrace'), 'Location').$new(loc);});
    }, -1);
    
    $def(self, '$class', function $Kernel_class$5() {
      var self = this;

      return self.$$class;
    });
    
    $def(self, '$copy_instance_variables', function $$copy_instance_variables(other) {
      var self = this;

      
      var keys = Object.keys(other), i, ii, name;
      for (i = 0, ii = keys.length; i < ii; i++) {
        name = keys[i];
        if (name.charAt(0) !== '$' && other.hasOwnProperty(name)) {
          self[name] = other[name];
        }
      }
    
    });
    
    $def(self, '$copy_singleton_methods', function $$copy_singleton_methods(other) {
      var self = this;

      
      var i, name, names, length;

      if (other.hasOwnProperty('$$meta') && other.$$meta !== null) {
        var other_singleton_class = Opal.get_singleton_class(other);
        var self_singleton_class = Opal.get_singleton_class(self);
        names = Object.getOwnPropertyNames(other_singleton_class.$$prototype);

        for (i = 0, length = names.length; i < length; i++) {
          name = names[i];
          if (Opal.is_method(name)) {
            self_singleton_class.$$prototype[name] = other_singleton_class.$$prototype[name];
          }
        }

        self_singleton_class.$$const = Object.assign({}, other_singleton_class.$$const);
        Object.setPrototypeOf(
          self_singleton_class.$$prototype,
          Object.getPrototypeOf(other_singleton_class.$$prototype)
        );
      }

      for (i = 0, names = Object.getOwnPropertyNames(other), length = names.length; i < length; i++) {
        name = names[i];
        if (name.charAt(0) === '$' && name.charAt(1) !== '$' && other.hasOwnProperty(name)) {
          self[name] = other[name];
        }
      }
    
    });
    
    $def(self, '$clone', function $$clone($kwargs) {
      var freeze, self = this, copy = nil;

      
      $kwargs = $ensure_kwargs($kwargs);
      
      freeze = $kwargs.$$smap["freeze"];if (freeze == null) freeze = nil;
      if (!(($truthy(freeze['$nil?']()) || ($eqeq(freeze, true))) || ($eqeq(freeze, false)))) {
        self.$raise($$('ArgumentError'), "unexpected value for freeze: " + (freeze.$class()));
      }      copy = self.$class().$allocate();
      copy.$copy_instance_variables(self);
      copy.$copy_singleton_methods(self);
      copy.$initialize_clone(self, $hash2(["freeze"], {"freeze": freeze}));
      if (($eqeq(freeze, true) || (($truthy(freeze['$nil?']()) && ($truthy(self['$frozen?']())))))) {
        copy.$freeze();
      }      return copy;
    }, -1);
    
    $def(self, '$initialize_clone', function $$initialize_clone(other, $kwargs) {
      var self = this;

      
      $kwargs = $ensure_kwargs($kwargs);
      
      $kwargs.$$smap["freeze"];      self.$initialize_copy(other);
      return self;
    }, -2);
    
    $def(self, '$define_singleton_method', function $$define_singleton_method(name, method) {
      var block = $$define_singleton_method.$$p || nil, self = this;

      $$define_singleton_method.$$p = null;
      return $send(self.$singleton_class(), 'define_method', [name, method], block.$to_proc());
    }, -2);
    
    $def(self, '$dup', function $$dup() {
      var self = this, copy = nil;

      
      copy = self.$class().$allocate();
      copy.$copy_instance_variables(self);
      copy.$initialize_dup(self);
      return copy;
    });
    
    $def(self, '$initialize_dup', function $$initialize_dup(other) {
      var self = this;

      return self.$initialize_copy(other)
    });
    
    $def(self, '$enum_for', function $$enum_for($a, $b) {
      var block = $$enum_for.$$p || nil, $post_args, method, args, self = this;

      $$enum_for.$$p = null;
      $post_args = $slice(arguments);
      
      if ($post_args.length > 0) method = $post_args.shift();if (method == null) method = "each";
      args = $post_args;
      return $send($$$('Enumerator'), 'for', [self, method].concat($to_a(args)), block.$to_proc());
    }, -1);
    
    $def(self, '$equal?', function $Kernel_equal$ques$6(other) {
      var self = this;

      return self === other;
    });
    
    $def(self, '$exit', function $$exit(status) {
      var $ret_or_1 = nil, block = nil;
      if ($gvars.__at_exit__ == null) $gvars.__at_exit__ = nil;

      
      if (status == null) status = true;
      $gvars.__at_exit__ = ($truthy(($ret_or_1 = $gvars.__at_exit__)) ? ($ret_or_1) : ([]));
      while (!($truthy($gvars.__at_exit__['$empty?']()))) {
      
        block = $gvars.__at_exit__.$pop();
        block.$call();
      }      
      if (status.$$is_boolean) {
        status = status ? 0 : 1;
      } else {
        status = $coerce_to(status, $$$('Integer'), 'to_int');
      }

      Opal.exit(status);
      return nil;
    }, -1);
    
    $def(self, '$extend', function $$extend($a) {
      var $post_args, mods, self = this;

      
      $post_args = $slice(arguments);
      mods = $post_args;
      
      if (mods.length == 0) {
        self.$raise($$$('ArgumentError'), "wrong number of arguments (given 0, expected 1+)");
      }

      $deny_frozen_access(self);

      var singleton = self.$singleton_class();

      for (var i = mods.length - 1; i >= 0; i--) {
        var mod = mods[i];

        if (!mod.$$is_module) {
          $Kernel.$raise($$$('TypeError'), "wrong argument type " + ((mod).$class()) + " (expected Module)");
        }

        (mod).$append_features(singleton);
        (mod).$extend_object(self);
        (mod).$extended(self);
      }
      return self;
    }, -1);
    
    $def(self, '$freeze', function $$freeze() {
      var self = this;

      
      if ($truthy(self['$frozen?']())) {
        return self
      }      
      if (typeof(self) === "object") {
        $freeze_props(self);
        return $freeze(self);
      }
      return self;
    });
    
    $def(self, '$frozen?', function $Kernel_frozen$ques$7() {
      var self = this;

      
      switch (typeof(self)) {
      case "string":
      case "symbol":
      case "number":
      case "boolean":
        return true;
      case "object":
        return (self.$$frozen || false);
      default:
        return false;
      }
    
    });
    
    $def(self, '$gets', function $$gets($a) {
      var $post_args, args;
      if ($gvars.stdin == null) $gvars.stdin = nil;

      
      $post_args = $slice(arguments);
      args = $post_args;
      return $send($gvars.stdin, 'gets', $to_a(args));
    }, -1);
    
    $def(self, '$hash', function $$hash() {
      var self = this;

      return self.$__id__()
    });
    
    $def(self, '$initialize_copy', $return_val(nil));
    var inspect_stack = [];
    
    $def(self, '$inspect', function $$inspect() {
      var self = this, ivs = nil, id = nil, pushed = nil, e = nil;

      return (function() { try {
      try {
        
        ivs = "";
        id = self.$__id__();
        if ($truthy((inspect_stack)['$include?'](id))) {
          ivs = " ...";
        } else {
          
          (inspect_stack)['$<<'](id);
          pushed = true;
          $send(self.$instance_variables(), 'each', [], function $$8(i){var self = $$8.$$s == null ? this : $$8.$$s, ivar = nil, inspect = nil;

            
            if (i == null) i = nil;
            ivar = self.$instance_variable_get(i);
            inspect = $$('Opal').$inspect(ivar);
            return (ivs = $rb_plus(ivs, " " + (i) + "=" + (inspect)));}, {$$s: self});
        };
        return "#<" + (self.$class()) + ":0x" + (id.$to_s(16)) + (ivs) + ">";
      } catch ($err) {
        if (Opal.rescue($err, [$$('StandardError')])) {(e = $err);
          try {
            return "#<" + (self.$class()) + ":0x" + (id.$to_s(16)) + ">"
          } finally { Opal.pop_exception(); }
        } else { throw $err; }
      }
      } finally {
        ($truthy(pushed) ? ((inspect_stack).$pop()) : nil);
      } })()
    });
    
    $def(self, '$instance_of?', function $Kernel_instance_of$ques$9(klass) {
      var self = this;

      
      if (!klass.$$is_class && !klass.$$is_module) {
        $Kernel.$raise($$$('TypeError'), "class or module required");
      }

      return self.$$class === klass;
    
    });
    
    $def(self, '$instance_variable_defined?', function $Kernel_instance_variable_defined$ques$10(name) {
      var self = this;

      
      name = $Opal['$instance_variable_name!'](name);
      return Opal.hasOwnProperty.call(self, name.substr(1));    });
    
    $def(self, '$instance_variable_get', function $$instance_variable_get(name) {
      var self = this;

      
      name = $Opal['$instance_variable_name!'](name);
      
      var ivar = self[Opal.ivar(name.substr(1))];

      return ivar == null ? nil : ivar;
    });
    
    $def(self, '$instance_variable_set', function $$instance_variable_set(name, value) {
      var self = this;

      
      $deny_frozen_access(self);
      name = $Opal['$instance_variable_name!'](name);
      return self[Opal.ivar(name.substr(1))] = value;    });
    
    $def(self, '$remove_instance_variable', function $$remove_instance_variable(name) {
      var self = this;

      
      name = $Opal['$instance_variable_name!'](name);
      
      var key = Opal.ivar(name.substr(1)),
          val;
      if (self.hasOwnProperty(key)) {
        val = self[key];
        delete self[key];
        return val;
      }
      return $Kernel.$raise($$$('NameError'), "instance variable " + (name) + " not defined");
    });
    
    $def(self, '$instance_variables', function $$instance_variables() {
      var self = this;

      
      var result = [], ivar;

      for (var name in self) {
        if (self.hasOwnProperty(name) && name.charAt(0) !== '$') {
          if (name.substr(-1) === '$') {
            ivar = name.slice(0, name.length - 1);
          } else {
            ivar = name;
          }
          result.push('@' + ivar);
        }
      }

      return result;
    
    });
    
    $def(self, '$Integer', function $$Integer(value, base) {
      
      var i, str, base_digits;

      if (!value.$$is_string) {
        if (base !== undefined) {
          $Kernel.$raise($$$('ArgumentError'), "base specified for non string value");
        }
        if (value === nil) {
          $Kernel.$raise($$$('TypeError'), "can't convert nil into Integer");
        }
        if (value.$$is_number) {
          if (value === Infinity || value === -Infinity || isNaN(value)) {
            $Kernel.$raise($$$('FloatDomainError'), value);
          }
          return Math.floor(value);
        }
        if (value['$respond_to?']("to_int")) {
          i = value.$to_int();
          if (i !== nil) {
            return i;
          }
        }
        return $Opal['$coerce_to!'](value, $$$('Integer'), "to_i");
      }

      if (value === "0") {
        return 0;
      }

      if (base === undefined) {
        base = 0;
      } else {
        base = $coerce_to(base, $$$('Integer'), 'to_int');
        if (base === 1 || base < 0 || base > 36) {
          $Kernel.$raise($$$('ArgumentError'), "invalid radix " + (base));
        }
      }

      str = value.toLowerCase();

      str = str.replace(/(\d)_(?=\d)/g, '$1');

      str = str.replace(/^(\s*[+-]?)(0[bodx]?)/, function (_, head, flag) {
        switch (flag) {
        case '0b':
          if (base === 0 || base === 2) {
            base = 2;
            return head;
          }
          // no-break
        case '0':
        case '0o':
          if (base === 0 || base === 8) {
            base = 8;
            return head;
          }
          // no-break
        case '0d':
          if (base === 0 || base === 10) {
            base = 10;
            return head;
          }
          // no-break
        case '0x':
          if (base === 0 || base === 16) {
            base = 16;
            return head;
          }
          // no-break
        }
        $Kernel.$raise($$$('ArgumentError'), "invalid value for Integer(): \"" + (value) + "\"");
      });

      base = (base === 0 ? 10 : base);

      base_digits = '0-' + (base <= 10 ? base - 1 : '9a-' + String.fromCharCode(97 + (base - 11)));

      if (!(new RegExp('^\\s*[+-]?[' + base_digits + ']+\\s*$')).test(str)) {
        $Kernel.$raise($$$('ArgumentError'), "invalid value for Integer(): \"" + (value) + "\"");
      }

      i = parseInt(str, base);

      if (isNaN(i)) {
        $Kernel.$raise($$$('ArgumentError'), "invalid value for Integer(): \"" + (value) + "\"");
      }

      return i;
    }, -2);
    
    $def(self, '$Float', function $$Float(value) {
      
      
      var str;

      if (value === nil) {
        $Kernel.$raise($$$('TypeError'), "can't convert nil into Float");
      }

      if (value.$$is_string) {
        str = value.toString();

        str = str.replace(/(\d)_(?=\d)/g, '$1');

        //Special case for hex strings only:
        if (/^\s*[-+]?0[xX][0-9a-fA-F]+\s*$/.test(str)) {
          return $Kernel.$Integer(str);
        }

        if (!/^\s*[-+]?[0-9]*\.?[0-9]+([eE][-+]?[0-9]+)?\s*$/.test(str)) {
          $Kernel.$raise($$$('ArgumentError'), "invalid value for Float(): \"" + (value) + "\"");
        }

        return parseFloat(str);
      }

      return $Opal['$coerce_to!'](value, $$$('Float'), "to_f");
    
    });
    
    $def(self, '$Hash', function $$Hash(arg) {
      
      
      if (($truthy(arg['$nil?']()) || ($eqeq(arg, [])))) {
        return $hash2([], {})
      }      if ($eqeqeq($$$('Hash'), arg)) {
        return arg
      }      return $Opal['$coerce_to!'](arg, $$$('Hash'), "to_hash");
    });
    
    $def(self, '$is_a?', function $Kernel_is_a$ques$11(klass) {
      var self = this;

      
      if (!klass.$$is_class && !klass.$$is_module) {
        $Kernel.$raise($$$('TypeError'), "class or module required");
      }

      return Opal.is_a(self, klass);
    
    });
    
    $def(self, '$itself', $return_self);
    
    $def(self, '$lambda', function $$lambda() {
      var block = $$lambda.$$p || nil;

      $$lambda.$$p = null;
      return Opal.lambda(block);    });
    
    $def(self, '$load', function $$load(file) {
      
      
      file = $Opal['$coerce_to!'](file, $$$('String'), "to_str");
      return Opal.load(file);
    });
    
    $def(self, '$loop', function $$loop() {
      var $yield = $$loop.$$p || nil, self = this, e = nil;

      $$loop.$$p = null;
      
      if (!($yield !== nil)) {
        return $send(self, 'enum_for', ["loop"], function $$12(){
          return $$$($$$('Float'), 'INFINITY')})
      }      while ($truthy(true)) {
      
        try {
          Opal.yieldX($yield, []);
        } catch ($err) {
          if (Opal.rescue($err, [$$$('StopIteration')])) {(e = $err);
            try {
              return e.$result()
            } finally { Opal.pop_exception(); }
          } else { throw $err; }
        }      }      return self;
    });
    
    $def(self, '$nil?', $return_val(false));
    
    $def(self, '$printf', function $$printf($a) {
      var $post_args, args, self = this;

      
      $post_args = $slice(arguments);
      args = $post_args;
      if ($truthy(args['$any?']())) {
        self.$print($send(self, 'format', $to_a(args)));
      }      return nil;
    }, -1);
    
    $def(self, '$proc', function $$proc() {
      var block = $$proc.$$p || nil;

      $$proc.$$p = null;
      if (!$truthy(block)) {
        $Kernel.$raise($$$('ArgumentError'), "tried to create Proc object without a block");
      }      block.$$is_lambda = false;
      return block;
    });
    
    $def(self, '$puts', function $$puts($a) {
      var $post_args, strs;
      if ($gvars.stdout == null) $gvars.stdout = nil;

      
      $post_args = $slice(arguments);
      strs = $post_args;
      return $send($gvars.stdout, 'puts', $to_a(strs));
    }, -1);
    
    $def(self, '$p', function $$p($a) {
      var $post_args, args;

      
      $post_args = $slice(arguments);
      args = $post_args;
      $send(args, 'each', [], function $$13(obj){        if ($gvars.stdout == null) $gvars.stdout = nil;

        
        if (obj == null) obj = nil;
        return $gvars.stdout.$puts(obj.$inspect());});
      if ($truthy($rb_le(args.$length(), 1))) {
        return args['$[]'](0)
      } else {
        return args
      }    }, -1);
    
    $def(self, '$print', function $$print($a) {
      var $post_args, strs;
      if ($gvars.stdout == null) $gvars.stdout = nil;

      
      $post_args = $slice(arguments);
      strs = $post_args;
      return $send($gvars.stdout, 'print', $to_a(strs));
    }, -1);
    
    $def(self, '$readline', function $$readline($a) {
      var $post_args, args;
      if ($gvars.stdin == null) $gvars.stdin = nil;

      
      $post_args = $slice(arguments);
      args = $post_args;
      return $send($gvars.stdin, 'readline', $to_a(args));
    }, -1);
    
    $def(self, '$warn', function $$warn($a, $b) {
      var $post_args, $kwargs, strs, uplevel, $c, $d, self = this, location = nil;
      if ($gvars.VERBOSE == null) $gvars.VERBOSE = nil;
      if ($gvars.stderr == null) $gvars.stderr = nil;

      
      $post_args = $slice(arguments);
      $kwargs = $extract_kwargs($post_args);
      $kwargs = $ensure_kwargs($kwargs);
      strs = $post_args;
      
      uplevel = $kwargs.$$smap["uplevel"];if (uplevel == null) uplevel = nil;
      if ($truthy(uplevel)) {
        
        uplevel = $Opal['$coerce_to!'](uplevel, $$$('Integer'), "to_str");
        if ($truthy($rb_lt(uplevel, 0))) {
          $Kernel.$raise($$$('ArgumentError'), "negative level (" + (uplevel) + ")");
        }        location = ($c = ($d = self.$caller($rb_plus(uplevel, 1), 1).$first(), ($d === nil || $d == null) ? nil : $d.$split(":in `")), ($c === nil || $c == null) ? nil : $c.$first());
        if ($truthy(location)) {
          location = "" + (location) + ": ";
        }        strs = $send(strs, 'map', [], function $$14(s){
          
          if (s == null) s = nil;
          return "" + (location) + "warning: " + (s);});
      }      if (($truthy($gvars.VERBOSE['$nil?']()) || ($truthy(strs['$empty?']())))) {
        return nil
      } else {
        return $send($gvars.stderr, 'puts', $to_a(strs))
      }    }, -1);
    
    $def(self, '$raise', function $$raise(exception, string, backtrace) {
            if ($gvars["!"] == null) $gvars["!"] = nil;
      if ($gvars["@"] == null) $gvars["@"] = nil;
      if (string == null) string = nil;
      if (backtrace == null) backtrace = nil;
      
      if (exception == null && $gvars["!"] !== nil) {
        throw $gvars["!"];
      }
      if (exception == null) {
        exception = $$$('RuntimeError').$new("");
      }
      else if ($respond_to(exception, '$to_str')) {
        exception = $$$('RuntimeError').$new(exception.$to_str());
      }
      // using respond_to? and not an undefined check to avoid method_missing matching as true
      else if (exception.$$is_class && $respond_to(exception, '$exception')) {
        exception = exception.$exception(string);
      }
      else if (exception.$$is_exception) ;
      else {
        exception = $$$('TypeError').$new("exception class/object expected");
      }

      if (backtrace !== nil) {
        exception.$set_backtrace(backtrace);
      }

      if ($gvars["!"] !== nil) {
        Opal.exceptions.push($gvars["!"]);
      }

      $gvars["!"] = exception;
      $gvars["@"] = (exception).$backtrace();

      throw exception;
    }, -1);
    
    $def(self, '$rand', function $$rand(max) {
      
      if (max === undefined) {
        return $$$($$$('Random'), 'DEFAULT').$rand();
      }

      if (max.$$is_number) {
        if (max < 0) {
          max = Math.abs(max);
        }

        if (max % 1 !== 0) {
          max = max.$to_i();
        }

        if (max === 0) {
          max = undefined;
        }
      }
      return $$$($$$('Random'), 'DEFAULT').$rand(max);
    }, -1);
    
    $def(self, '$respond_to?', function $Kernel_respond_to$ques$15(name, include_all) {
      var self = this;

      
      if (include_all == null) include_all = false;
      
      var body = self[$jsid(name)];

      if (typeof(body) === "function" && !body.$$stub) {
        return true;
      }

      if (self['$respond_to_missing?'].$$pristine === true) {
        return false;
      } else {
        return self['$respond_to_missing?'](name, include_all);
      }
    }, -2);
    
    $def(self, '$respond_to_missing?', function $Kernel_respond_to_missing$ques$16(method_name, include_all) {
      return false;
    }, -2);
    $Opal.$pristine(self, "respond_to?", "respond_to_missing?");
    
    $def(self, '$require', function $$require(file) {
      
      
      // As Object.require refers to Kernel.require once Kernel has been loaded the String
      // class may not be available yet, the coercion requires both  String and Array to be loaded.
      if (typeof file !== 'string' && Opal.String && Opal.Array) {
        (file = $Opal['$coerce_to!'](file, $$$('String'), "to_str"));
      }
      return Opal.require(file)
    
    });
    
    $def(self, '$require_relative', function $$require_relative(file) {
      
      
      $Opal['$try_convert!'](file, $$$('String'), "to_str");
      file = $$$('File').$expand_path($$$('File').$join(Opal.current_file, "..", file));
      return Opal.require(file);
    });
    
    $def(self, '$require_tree', function $$require_tree(path, $kwargs) {
      var autoload;

      
      $kwargs = $ensure_kwargs($kwargs);
      
      autoload = $kwargs.$$smap["autoload"];if (autoload == null) autoload = false;
      
      var result = [];

      path = $$$('File').$expand_path(path);
      path = Opal.normalize(path);
      if (path === '.') path = '';
      for (var name in Opal.modules) {
        if ((name)['$start_with?'](path)) {
          if(!autoload) {
            result.push([name, Opal.require(name)]);
          } else {
            result.push([name, true]); // do nothing, delegated to a autoloading
          }
        }
      }

      return result;
    }, -2);
    
    $def(self, '$singleton_class', function $$singleton_class() {
      var self = this;

      return Opal.get_singleton_class(self);
    });
    
    $def(self, '$sleep', function $$sleep(seconds) {
      
      
      if (seconds == null) seconds = nil;
      
      if (seconds === nil) {
        $Kernel.$raise($$$('TypeError'), "can't convert NilClass into time interval");
      }
      if (!seconds.$$is_number) {
        $Kernel.$raise($$$('TypeError'), "can't convert " + (seconds.$class()) + " into time interval");
      }
      if (seconds < 0) {
        $Kernel.$raise($$$('ArgumentError'), "time interval must be positive");
      }
      var get_time = Opal.global.performance ?
        function() {return performance.now()} :
        function() {return new Date()};

      var t = get_time();
      while (get_time() - t <= seconds * 1000);
      return Math.round(seconds);
    }, -1);
    
    $def(self, '$srand', function $$srand(seed) {
      
      
      if (seed == null) seed = $$('Random').$new_seed();
      return $$$('Random').$srand(seed);
    }, -1);
    
    $def(self, '$String', function $$String(str) {
      var $ret_or_1 = nil;

      if ($truthy(($ret_or_1 = $Opal['$coerce_to?'](str, $$$('String'), "to_str")))) {
        return $ret_or_1
      } else {
        return $Opal['$coerce_to!'](str, $$$('String'), "to_s")
      }
    });
    
    $def(self, '$tap', function $$tap() {
      var block = $$tap.$$p || nil, self = this;

      $$tap.$$p = null;
      Opal.yield1(block, self);
      return self;
    });
    
    $def(self, '$to_proc', $return_self);
    
    $def(self, '$to_s', function $$to_s() {
      var self = this;

      return "#<" + (self.$class()) + ":0x" + (self.$__id__().$to_s(16)) + ">"
    });
    
    $def(self, '$catch', function $Kernel_catch$17(tag) {
      var $yield = $Kernel_catch$17.$$p || nil, $ret_or_1 = nil, e = nil;

      $Kernel_catch$17.$$p = null;
      
      if (tag == null) tag = nil;
      try {
        
        tag = ($truthy(($ret_or_1 = tag)) ? ($ret_or_1) : ($Object.$new()));
        return Opal.yield1($yield, tag);;
      } catch ($err) {
        if (Opal.rescue($err, [$$$('UncaughtThrowError')])) {(e = $err);
          try {
            
            if ($eqeq(e.$tag(), tag)) {
              return e.$value()
            };
            return $Kernel.$raise();
          } finally { Opal.pop_exception(); }
        } else { throw $err; }
      }    }, -1);
    
    $def(self, '$throw', function $Kernel_throw$18(tag, obj) {
      
      
      if (obj == null) obj = nil;
      return $Kernel.$raise($$$('UncaughtThrowError').$new(tag, obj));
    }, -2);
    
    $def(self, '$open', function $$open($a) {
      var block = $$open.$$p || nil, $post_args, args;

      $$open.$$p = null;
      $post_args = $slice(arguments);
      args = $post_args;
      return $send($$$('File'), 'open', $to_a(args), block.$to_proc());
    }, -1);
    
    $def(self, '$yield_self', function $$yield_self() {
      var $yield = $$yield_self.$$p || nil, self = this;

      $$yield_self.$$p = null;
      
      if (!($yield !== nil)) {
        return $send(self, 'enum_for', ["yield_self"], $return_val(1))
      }      return Opal.yield1($yield, self);    });
    $alias(self, "fail", "raise");
    $alias(self, "kind_of?", "is_a?");
    $alias(self, "object_id", "__id__");
    $alias(self, "public_send", "__send__");
    $alias(self, "send", "__send__");
    $alias(self, "then", "yield_self");
    return $alias(self, "to_enum", "enum_for");
  })('::', $nesting);
  return (function($base, $super) {
    var self = $klass($base, $super, 'Object');

    
    
    delete $Object.$$prototype.$require;
    return self.$include($Kernel);
  })('::', null);
};

Opal.modules["corelib/main"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $return_val = Opal.return_val, $def = Opal.def, $Object = Opal.Object, $slice = Opal.slice, $Kernel = Opal.Kernel, self = Opal.top; Opal.nil;

  Opal.add_stubs('include,raise');
  return (function(self, $parent_nesting) {
    
    
    
    $def(self, '$to_s', $return_val("main"));
    
    $def(self, '$include', function $$include(mod) {
      
      return $Object.$include(mod)
    });
    
    $def(self, '$autoload', function $$autoload($a) {
      var $post_args, args;

      
      $post_args = $slice(arguments);
      args = $post_args;
      return Opal.Object.$autoload.apply(Opal.Object, args);    }, -1);
    return $def(self, '$using', function $$using(mod) {
      
      return $Kernel.$raise("main.using is permitted only at toplevel")
    });
  })(Opal.get_singleton_class(self))
};

Opal.modules["corelib/error/errno"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $module = Opal.module, $truthy = Opal.truthy, $rb_plus = Opal.rb_plus, $send2 = Opal.send2, $find_super = Opal.find_super, $def = Opal.def, $klass = Opal.klass, nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('+,errno,class,attr_reader');
  
  (function($base, $parent_nesting) {
    var self = $module($base, 'Errno');

    var errors = nil, klass = nil;

    
    errors = [["EINVAL", "Invalid argument", 22], ["EEXIST", "File exists", 17], ["EISDIR", "Is a directory", 21], ["EMFILE", "Too many open files", 24], ["ESPIPE", "Illegal seek", 29], ["EACCES", "Permission denied", 13], ["EPERM", "Operation not permitted", 1], ["ENOENT", "No such file or directory", 2], ["ENAMETOOLONG", "File name too long", 36]];
    klass = nil;
    
    var i;
    for (i = 0; i < errors.length; i++) {
      (function() { // Create a closure
        var class_name = errors[i][0];
        var default_message = errors[i][1];
        var errno = errors[i][2];

        klass = Opal.klass(self, Opal.SystemCallError, class_name);
        klass.errno = errno;

        (function(self, $parent_nesting) {
      
      return $def(self, '$new', function $new$1(name) {
        var self = this, message = nil;

        $new$1.$$p = null;
        
        if (name == null) name = nil;
        message = default_message;
        if ($truthy(name)) {
          message = $rb_plus(message, " - " + (name));
        }        return $send2(self, $find_super(self, 'new', $new$1, false, true), 'new', [message], null);
      }, -1)
    })(Opal.get_singleton_class(klass));
      })();
    }
  })('::');
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'SystemCallError');

    
    
    $def(self, '$errno', function $$errno() {
      var self = this;

      return self.$class().$errno()
    });
    return (function(self, $parent_nesting) {
      
      return self.$attr_reader("errno")
    })(Opal.get_singleton_class(self));
  })('::', $$$('StandardError'));
};

Opal.modules["corelib/error"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $slice = Opal.slice, $gvars = Opal.gvars, $defs = Opal.defs, $send = Opal.send, $to_a = Opal.to_a, $def = Opal.def, $truthy = Opal.truthy, $hash2 = Opal.hash2, $Kernel = Opal.Kernel, $not = Opal.not, $rb_plus = Opal.rb_plus, $eqeq = Opal.eqeq, $Object = Opal.Object, $ensure_kwargs = Opal.ensure_kwargs, $send2 = Opal.send2, $find_super = Opal.find_super, $module = Opal.module, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('new,map,backtrace,clone,to_s,merge,tty?,[],include?,raise,dup,empty?,!,caller,shift,+,class,join,cause,full_message,==,reverse,split,autoload,attr_reader,inspect');
  
  (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Exception');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting), $proto = self.$$prototype;

    $proto.message = nil;
    
    Opal.prop(self.$$prototype, '$$is_exception', true);
    var stack_trace_limit;
    Error.stackTraceLimit = 100;
    $defs(self, '$new', function $Exception_new$1($a) {
      var $post_args, args, self = this;
      if ($gvars["!"] == null) $gvars["!"] = nil;

      
      $post_args = $slice(arguments);
      args = $post_args;
      
      var message   = (args.length > 0) ? args[0] : nil;
      var error     = new self.$$constructor(message);
      error.name    = self.$$name;
      error.message = message;
      error.cause   = $gvars["!"];
      Opal.send(error, error.$initialize, args);

      // Error.captureStackTrace() will use .name and .toString to build the
      // first line of the stack trace so it must be called after the error
      // has been initialized.
      // https://nodejs.org/dist/latest-v6.x/docs/api/errors.html
      if (Opal.config.enable_stack_trace && Error.captureStackTrace) {
        // Passing Kernel.raise will cut the stack trace from that point above
        Error.captureStackTrace(error, stack_trace_limit);
      }

      return error;
    }, -1);
    stack_trace_limit = self.$new;
    $defs(self, '$exception', function $$exception($a) {
      var $post_args, args, self = this;

      
      $post_args = $slice(arguments);
      args = $post_args;
      return $send(self, 'new', $to_a(args));
    }, -1);
    
    $def(self, '$initialize', function $$initialize($a) {
      var $post_args, args, self = this;

      
      $post_args = $slice(arguments);
      args = $post_args;
      return self.message = (args.length > 0) ? args[0] : nil;    }, -1);
    
    // Convert backtrace from any format to Ruby format
    function correct_backtrace(backtrace) {
      var new_bt = [], m;

      for (var i = 0; i < backtrace.length; i++) {
        var loc = backtrace[i];
        if (!loc || !loc.$$is_string) ;
        /* Chromium format */
        else if ((m = loc.match(/^    at (.*?) \((.*?)\)$/))) {
          new_bt.push(m[2] + ":in `" + m[1] + "'");
        }
        else if ((m = loc.match(/^    at (.*?)$/))) {
          new_bt.push(m[1] + ":in `undefined'");
        }
        /* Node format */
        else if ((m = loc.match(/^  from (.*?)$/))) {
          new_bt.push(m[1]);
        }
        /* Mozilla/Apple format */
        else if ((m = loc.match(/^(.*?)@(.*?)$/))) {
          new_bt.push(m[2] + ':in `' + m[1] + "'");
        }
      }

      return new_bt;
    }
    
    $def(self, '$backtrace', function $$backtrace() {
      var self = this;

      
      if (self.backtrace) {
        // nil is a valid backtrace
        return self.backtrace;
      }

      var backtrace = self.stack;

      if (typeof(backtrace) !== 'undefined' && backtrace.$$is_string) {
        return self.backtrace = correct_backtrace(backtrace.split("\n"));
      }
      else if (backtrace) {
        return self.backtrace = correct_backtrace(backtrace);
      }

      return [];
    
    });
    
    $def(self, '$backtrace_locations', function $$backtrace_locations() {
      var $a, self = this;

      
      if (self.backtrace_locations) return self.backtrace_locations;
      self.backtrace_locations = ($a = self.$backtrace(), ($a === nil || $a == null) ? nil : $send($a, 'map', [], function $$2(loc){
        
        if (loc == null) loc = nil;
        return $$$($$$($$$('Thread'), 'Backtrace'), 'Location').$new(loc);}));
      return self.backtrace_locations;
    
    });
    
    $def(self, '$cause', function $$cause() {
      var self = this;

      return self.cause || nil;
    });
    
    $def(self, '$exception', function $$exception(str) {
      var self = this;

      
      if (str == null) str = nil;
      
      if (str === nil || self === str) {
        return self;
      }

      var cloned = self.$clone();
      cloned.message = str;
      if (self.backtrace) cloned.backtrace = self.backtrace.$dup();
      cloned.stack = self.stack;
      cloned.cause = self.cause;
      return cloned;
    }, -1);
    
    $def(self, '$message', function $$message() {
      var self = this;

      return self.$to_s()
    });
    
    $def(self, '$full_message', function $$full_message(kwargs) {
      var $b, self = this, $ret_or_1 = nil, highlight = nil, order = nil, bold_underline = nil, bold = nil, reset = nil, bt = nil, first = nil, msg = nil;
      if ($gvars.stderr == null) $gvars.stderr = nil;

      
      if (kwargs == null) kwargs = nil;
      if (!$truthy((($$('Hash', 'skip_raise')) ? 'constant' : nil))) {
        return "" + (self.message) + "\n" + (self.stack)
      }      kwargs = $hash2(["highlight", "order"], {"highlight": $gvars.stderr['$tty?'](), "order": "top"}).$merge(($truthy(($ret_or_1 = kwargs)) ? ($ret_or_1) : ($hash2([], {}))));
      $b = [kwargs['$[]']("highlight"), kwargs['$[]']("order")], (highlight = $b[0]), (order = $b[1]);
      if (!$truthy([true, false]['$include?'](highlight))) {
        $Kernel.$raise($$$('ArgumentError'), "expected true or false as highlight: " + (highlight));
      }      if (!$truthy(["top", "bottom"]['$include?'](order))) {
        $Kernel.$raise($$$('ArgumentError'), "expected :top or :bottom as order: " + (order));
      }      if ($truthy(highlight)) {
        
        bold_underline = "\u001b[1;4m";
        bold = "\u001b[1m";
        reset = "\u001b[m";
      } else {
        bold_underline = (bold = (reset = ""));
      }      bt = self.$backtrace().$dup();
      if (($not(bt) || ($truthy(bt['$empty?']())))) {
        bt = self.$caller();
      }      first = bt.$shift();
      msg = "" + (first) + ": ";
      msg = $rb_plus(msg, "" + (bold) + (self.$to_s()) + " (" + (bold_underline) + (self.$class()) + (reset) + (bold) + ")" + (reset) + "\n");
      msg = $rb_plus(msg, $send(bt, 'map', [], function $$3(loc){
        
        if (loc == null) loc = nil;
        return "\tfrom " + (loc) + "\n";}).$join());
      if ($truthy(self.$cause())) {
        msg = $rb_plus(msg, self.$cause().$full_message($hash2(["highlight"], {"highlight": highlight})));
      }      if ($eqeq(order, "bottom")) {
        
        msg = msg.$split("\n").$reverse().$join("\n");
        msg = $rb_plus("" + (bold) + "Traceback" + (reset) + " (most recent call last):\n", msg);
      }      return msg;
    }, -1);
    
    $def(self, '$inspect', function $$inspect() {
      var self = this, as_str = nil;

      
      as_str = self.$to_s();
      if ($truthy(as_str['$empty?']())) {
        return self.$class().$to_s()
      } else {
        return "#<" + (self.$class().$to_s()) + ": " + (self.$to_s()) + ">"
      }    });
    
    $def(self, '$set_backtrace', function $$set_backtrace(backtrace) {
      var self = this;

      
      var valid = true, i, ii;

      if (backtrace === nil) {
        self.backtrace = nil;
        self.stack = '';
      } else if (backtrace.$$is_string) {
        self.backtrace = [backtrace];
        self.stack = '  from ' + backtrace;
      } else {
        if (backtrace.$$is_array) {
          for (i = 0, ii = backtrace.length; i < ii; i++) {
            if (!backtrace[i].$$is_string) {
              valid = false;
              break;
            }
          }
        } else {
          valid = false;
        }

        if (valid === false) {
          $Kernel.$raise($$$('TypeError'), "backtrace must be Array of String");
        }

        self.backtrace = backtrace;
        self.stack = $send((backtrace), 'map', [], function $$4(i){
        
        if (i == null) i = nil;
        return $rb_plus("  from ", i);}).join("\n");
      }

      return backtrace;
    
    });
    return $def(self, '$to_s', function $$to_s() {
      var self = this, $ret_or_1 = nil, $ret_or_2 = nil;

      if ($truthy(($ret_or_1 = ($truthy(($ret_or_2 = self.message)) ? (self.message.$to_s()) : ($ret_or_2))))) {
        return $ret_or_1
      } else {
        return self.$class().$to_s()
      }
    });
  })('::', Error, $nesting);
  $klass('::', $$$('Exception'), 'ScriptError');
  $klass('::', $$$('ScriptError'), 'SyntaxError');
  $klass('::', $$$('ScriptError'), 'LoadError');
  $klass('::', $$$('ScriptError'), 'NotImplementedError');
  $klass('::', $$$('Exception'), 'SystemExit');
  $klass('::', $$$('Exception'), 'NoMemoryError');
  $klass('::', $$$('Exception'), 'SignalException');
  $klass('::', $$$('SignalException'), 'Interrupt');
  $klass('::', $$$('Exception'), 'SecurityError');
  $klass('::', $$$('Exception'), 'SystemStackError');
  $klass('::', $$$('Exception'), 'StandardError');
  $klass('::', $$$('StandardError'), 'EncodingError');
  $klass('::', $$$('StandardError'), 'ZeroDivisionError');
  $klass('::', $$$('StandardError'), 'NameError');
  $klass('::', $$$('NameError'), 'NoMethodError');
  $klass('::', $$$('StandardError'), 'RuntimeError');
  $klass('::', $$$('RuntimeError'), 'FrozenError');
  $klass('::', $$$('StandardError'), 'LocalJumpError');
  $klass('::', $$$('StandardError'), 'TypeError');
  $klass('::', $$$('StandardError'), 'ArgumentError');
  $klass('::', $$$('ArgumentError'), 'UncaughtThrowError');
  $klass('::', $$$('StandardError'), 'IndexError');
  $klass('::', $$$('IndexError'), 'StopIteration');
  $klass('::', $$$('StopIteration'), 'ClosedQueueError');
  $klass('::', $$$('IndexError'), 'KeyError');
  $klass('::', $$$('StandardError'), 'RangeError');
  $klass('::', $$$('RangeError'), 'FloatDomainError');
  $klass('::', $$$('StandardError'), 'IOError');
  $klass('::', $$$('IOError'), 'EOFError');
  $klass('::', $$$('StandardError'), 'SystemCallError');
  $klass('::', $$$('StandardError'), 'RegexpError');
  $klass('::', $$$('StandardError'), 'ThreadError');
  $klass('::', $$$('StandardError'), 'FiberError');
  $Object.$autoload("Errno", "corelib/error/errno");
  (function($base, $super) {
    var self = $klass($base, $super, 'FrozenError');

    
    
    self.$attr_reader("receiver");
    return $def(self, '$initialize', function $$initialize(message, $kwargs) {
      var receiver, self = this;

      $$initialize.$$p = null;
      
      $kwargs = $ensure_kwargs($kwargs);
      
      receiver = $kwargs.$$smap["receiver"];if (receiver == null) receiver = nil;
      $send2(self, $find_super(self, 'initialize', $$initialize, false, true), 'initialize', [message], null);
      return (self.receiver = receiver);
    }, -2);
  })('::', $$$('RuntimeError'));
  (function($base, $super) {
    var self = $klass($base, $super, 'UncaughtThrowError');

    var $proto = self.$$prototype;

    $proto.tag = nil;
    
    self.$attr_reader("tag", "value");
    return $def(self, '$initialize', function $$initialize(tag, value) {
      var self = this;

      $$initialize.$$p = null;
      
      if (value == null) value = nil;
      self.tag = tag;
      self.value = value;
      return $send2(self, $find_super(self, 'initialize', $$initialize, false, true), 'initialize', ["uncaught throw " + (self.tag.$inspect())], null);
    }, -2);
  })('::', $$$('ArgumentError'));
  (function($base, $super) {
    var self = $klass($base, $super, 'NameError');

    
    
    self.$attr_reader("name");
    return $def(self, '$initialize', function $$initialize(message, name) {
      var self = this;

      $$initialize.$$p = null;
      
      if (name == null) name = nil;
      $send2(self, $find_super(self, 'initialize', $$initialize, false, true), 'initialize', [message], null);
      return (self.name = name);
    }, -2);
  })('::', null);
  (function($base, $super) {
    var self = $klass($base, $super, 'NoMethodError');

    
    
    self.$attr_reader("args");
    return $def(self, '$initialize', function $$initialize(message, name, args) {
      var self = this;

      $$initialize.$$p = null;
      
      if (name == null) name = nil;
      if (args == null) args = [];
      $send2(self, $find_super(self, 'initialize', $$initialize, false, true), 'initialize', [message, name], null);
      return (self.args = args);
    }, -2);
  })('::', null);
  (function($base, $super) {
    var self = $klass($base, $super, 'StopIteration');

    
    return self.$attr_reader("result")
  })('::', null);
  (function($base, $super) {
    var self = $klass($base, $super, 'KeyError');

    var $proto = self.$$prototype;

    $proto.receiver = $proto.key = nil;
    
    
    $def(self, '$initialize', function $$initialize(message, $kwargs) {
      var receiver, key, self = this;

      $$initialize.$$p = null;
      
      $kwargs = $ensure_kwargs($kwargs);
      
      receiver = $kwargs.$$smap["receiver"];if (receiver == null) receiver = nil;
      
      key = $kwargs.$$smap["key"];if (key == null) key = nil;
      $send2(self, $find_super(self, 'initialize', $$initialize, false, true), 'initialize', [message], null);
      self.receiver = receiver;
      return (self.key = key);
    }, -2);
    
    $def(self, '$receiver', function $$receiver() {
      var self = this, $ret_or_1 = nil;

      if ($truthy(($ret_or_1 = self.receiver))) {
        return $ret_or_1
      } else {
        return $Kernel.$raise($$$('ArgumentError'), "no receiver is available")
      }
    });
    return $def(self, '$key', function $$key() {
      var self = this, $ret_or_1 = nil;

      if ($truthy(($ret_or_1 = self.key))) {
        return $ret_or_1
      } else {
        return $Kernel.$raise($$$('ArgumentError'), "no key is available")
      }
    });
  })('::', null);
  return (function($base, $parent_nesting) {
    var self = $module($base, 'JS');

    var $nesting = [self].concat($parent_nesting);

    return ($klass($nesting[0], null, 'Error'), nil)
  })('::', $nesting);
};

Opal.modules["corelib/constants"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $const_set = Opal.const_set; Opal.nil; var $$$ = Opal.$$$;

  
  $const_set('::', 'RUBY_PLATFORM', "opal");
  $const_set('::', 'RUBY_ENGINE', "opal");
  $const_set('::', 'RUBY_VERSION', "3.2.0");
  $const_set('::', 'RUBY_ENGINE_VERSION', "1.7.3");
  $const_set('::', 'RUBY_RELEASE_DATE', "2023-03-23");
  $const_set('::', 'RUBY_PATCHLEVEL', 0);
  $const_set('::', 'RUBY_REVISION', "0");
  $const_set('::', 'RUBY_COPYRIGHT', "opal - Copyright (C) 2011-2023 Adam Beynon and the Opal contributors");
  return $const_set('::', 'RUBY_DESCRIPTION', "opal " + ($$$('RUBY_ENGINE_VERSION')) + " (" + ($$$('RUBY_RELEASE_DATE')) + " revision " + ($$$('RUBY_REVISION')) + ")");
};

Opal.modules["opal/base"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $Object = Opal.Object; Opal.nil;

  Opal.add_stubs('require');
  
  $Object.$require("corelib/runtime");
  $Object.$require("corelib/helpers");
  $Object.$require("corelib/module");
  $Object.$require("corelib/class");
  $Object.$require("corelib/basic_object");
  $Object.$require("corelib/kernel");
  $Object.$require("corelib/main");
  $Object.$require("corelib/error");
  return $Object.$require("corelib/constants");
};

Opal.modules["corelib/nil"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $Kernel = Opal.Kernel, $def = Opal.def, $return_val = Opal.return_val, $ensure_kwargs = Opal.ensure_kwargs, $NilClass = Opal.NilClass, $slice = Opal.slice, $truthy = Opal.truthy, $rb_gt = Opal.rb_gt, $alias = Opal.alias, nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('raise,name,new,>,length,Rational,to_i');
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'NilClass');

    
    self.$$prototype.$$meta = self;
    (function(self, $parent_nesting) {
      
      
      
      $def(self, '$allocate', function $$allocate() {
        var self = this;

        return $Kernel.$raise($$$('TypeError'), "allocator undefined for " + (self.$name()))
      });
      
      
      Opal.udef(self, '$' + "new");      return nil;    })(Opal.get_singleton_class(self));
    
    $def(self, '$!', $return_val(true));
    
    $def(self, '$&', $return_val(false));
    
    $def(self, '$|', function $NilClass_$$1(other) {
      
      return other !== false && other !== nil;
    });
    
    $def(self, '$^', function $NilClass_$$2(other) {
      
      return other !== false && other !== nil;
    });
    
    $def(self, '$==', function $NilClass_$eq_eq$3(other) {
      
      return other === nil;
    });
    
    $def(self, '$dup', $return_val(nil));
    
    $def(self, '$clone', function $$clone($kwargs) {

      
      $kwargs = $ensure_kwargs($kwargs);
      
      $kwargs.$$smap["freeze"];      return nil;
    }, -1);
    
    $def(self, '$inspect', $return_val("nil"));
    
    $def(self, '$nil?', $return_val(true));
    
    $def(self, '$singleton_class', function $$singleton_class() {
      
      return $NilClass
    });
    
    $def(self, '$to_a', function $$to_a() {
      
      return []
    });
    
    $def(self, '$to_h', function $$to_h() {
      
      return Opal.hash();
    });
    
    $def(self, '$to_i', $return_val(0));
    
    $def(self, '$to_s', $return_val(""));
    
    $def(self, '$to_c', function $$to_c() {
      
      return $$$('Complex').$new(0, 0)
    });
    
    $def(self, '$rationalize', function $$rationalize($a) {
      var $post_args, args;

      
      $post_args = $slice(arguments);
      args = $post_args;
      if ($truthy($rb_gt(args.$length(), 1))) {
        $Kernel.$raise($$$('ArgumentError'));
      }      return $Kernel.$Rational(0, 1);
    }, -1);
    
    $def(self, '$to_r', function $$to_r() {
      
      return $Kernel.$Rational(0, 1)
    });
    
    $def(self, '$instance_variables', function $$instance_variables() {
      
      return []
    });
    return $alias(self, "to_f", "to_i");
  })('::', null)
};

Opal.modules["corelib/boolean"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $Kernel = Opal.Kernel, $def = Opal.def, $return_self = Opal.return_self, $ensure_kwargs = Opal.ensure_kwargs, $slice = Opal.slice, $truthy = Opal.truthy, $send2 = Opal.send2, $find_super = Opal.find_super, $to_a = Opal.to_a, $alias = Opal.alias, nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('raise,name,==,to_s,__id__');
  
  (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Boolean');

    
    Opal.prop(self.$$prototype, '$$is_boolean', true);
    
    var properties = ['$$class', '$$meta'];

    for (var i = 0; i < properties.length; i++) {
      Object.defineProperty(self.$$prototype, properties[i], {
        configurable: true,
        enumerable: false,
        get: function() {
          return this == true  ? Opal.TrueClass :
                 this == false ? Opal.FalseClass :
                                 Opal.Boolean;
        }
      });
    }

    Object.defineProperty(self.$$prototype, "$$id", {
      configurable: true,
      enumerable: false,
      get: function() {
        return this == true  ? 2 :
               this == false ? 0 :
                               nil;
      }
    });
    (function(self, $parent_nesting) {
      
      
      
      $def(self, '$allocate', function $$allocate() {
        var self = this;

        return $Kernel.$raise($$$('TypeError'), "allocator undefined for " + (self.$name()))
      });
      
      
      Opal.udef(self, '$' + "new");      return nil;    })(Opal.get_singleton_class(self));
    
    $def(self, '$__id__', function $$__id__() {
      var self = this;

      return self.valueOf() ? 2 : 0;
    });
    
    $def(self, '$!', function $Boolean_$excl$1() {
      var self = this;

      return self != true;
    });
    
    $def(self, '$&', function $Boolean_$$2(other) {
      var self = this;

      return (self == true) ? (other !== false && other !== nil) : false;
    });
    
    $def(self, '$|', function $Boolean_$$3(other) {
      var self = this;

      return (self == true) ? true : (other !== false && other !== nil);
    });
    
    $def(self, '$^', function $Boolean_$$4(other) {
      var self = this;

      return (self == true) ? (other === false || other === nil) : (other !== false && other !== nil);
    });
    
    $def(self, '$==', function $Boolean_$eq_eq$5(other) {
      var self = this;

      return (self == true) === other.valueOf();
    });
    
    $def(self, '$singleton_class', function $$singleton_class() {
      var self = this;

      return self.$$meta;
    });
    
    $def(self, '$to_s', function $$to_s() {
      var self = this;

      return (self == true) ? 'true' : 'false';
    });
    
    $def(self, '$dup', $return_self);
    
    $def(self, '$clone', function $$clone($kwargs) {
      var self = this;

      
      $kwargs = $ensure_kwargs($kwargs);
      
      $kwargs.$$smap["freeze"];      return self;
    }, -1);
    
    $def(self, '$method_missing', function $$method_missing(method, $a) {
      var block = $$method_missing.$$p || nil, $post_args, args, self = this;

      $$method_missing.$$p = null;
      $post_args = $slice(arguments, 1);
      args = $post_args;
      var body = self.$$class.$$prototype[Opal.jsid(method)];
      if (!$truthy(typeof body !== 'undefined' && !body.$$stub)) {
        $send2(self, $find_super(self, 'method_missing', $$method_missing, false, true), 'method_missing', [method].concat($to_a(args)), block);
      }      return Opal.send(self, body, args, block);
    }, -2);
    
    $def(self, '$respond_to_missing?', function $Boolean_respond_to_missing$ques$6(method, _include_all) {
      var self = this;
      var body = self.$$class.$$prototype[Opal.jsid(method)];
      return typeof body !== 'undefined' && !body.$$stub;    }, -2);
    $alias(self, "eql?", "==");
    $alias(self, "equal?", "==");
    $alias(self, "inspect", "to_s");
    return $alias(self, "object_id", "__id__");
  })('::', Boolean);
  $klass('::', $$$('Boolean'), 'TrueClass');
  return ($klass('::', $$$('Boolean'), 'FalseClass'), nil);
};

Opal.modules["corelib/comparable"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $truthy = Opal.truthy, $module = Opal.module, $rb_gt = Opal.rb_gt, $rb_lt = Opal.rb_lt, $eqeqeq = Opal.eqeqeq, $Kernel = Opal.Kernel, $def = Opal.def, nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('>,<,===,raise,class,<=>,equal?');
  return (function($base) {
    var self = $module($base, 'Comparable');

    var $ret_or_1 = nil;

    
    
    function normalize(what) {
      if (Opal.is_a(what, Opal.Integer)) { return what; }

      if ($rb_gt(what, 0)) { return 1; }
      if ($rb_lt(what, 0)) { return -1; }
      return 0;
    }

    function fail_comparison(lhs, rhs) {
      var class_name;
      (($eqeqeq(nil, ($ret_or_1 = rhs)) || (($eqeqeq(true, $ret_or_1) || (($eqeqeq(false, $ret_or_1) || (($eqeqeq($$$('Integer'), $ret_or_1) || ($eqeqeq($$$('Float'), $ret_or_1))))))))) ? (class_name = rhs.$inspect()) : (class_name = rhs.$$class));
      $Kernel.$raise($$$('ArgumentError'), "comparison of " + ((lhs).$class()) + " with " + (class_name) + " failed");
    }

    function cmp_or_fail(lhs, rhs) {
      var cmp = (lhs)['$<=>'](rhs);
      if (!$truthy(cmp)) fail_comparison(lhs, rhs);
      return normalize(cmp);
    }
    
    $def(self, '$==', function $Comparable_$eq_eq$1(other) {
      var self = this, cmp = nil;

      
      if ($truthy(self['$equal?'](other))) {
        return true
      }      
      if (self["$<=>"] == Opal.Kernel["$<=>"]) {
        return false;
      }

      // check for infinite recursion
      if (self.$$comparable) {
        self.$$comparable = false;
        return false;
      }
      if (!$truthy((cmp = self['$<=>'](other)))) {
        return false
      }      return normalize(cmp) == 0;    });
    
    $def(self, '$>', function $Comparable_$gt$2(other) {
      var self = this;

      return cmp_or_fail(self, other) > 0;
    });
    
    $def(self, '$>=', function $Comparable_$gt_eq$3(other) {
      var self = this;

      return cmp_or_fail(self, other) >= 0;
    });
    
    $def(self, '$<', function $Comparable_$lt$4(other) {
      var self = this;

      return cmp_or_fail(self, other) < 0;
    });
    
    $def(self, '$<=', function $Comparable_$lt_eq$5(other) {
      var self = this;

      return cmp_or_fail(self, other) <= 0;
    });
    
    $def(self, '$between?', function $Comparable_between$ques$6(min, max) {
      var self = this;

      
      if ($rb_lt(self, min)) {
        return false
      }      if ($rb_gt(self, max)) {
        return false
      }      return true;
    });
    return $def(self, '$clamp', function $$clamp(min, max) {
      var self = this;

      
      if (max == null) max = nil;
      
      var c, excl;

      if (max === nil) {
        // We are dealing with a new Ruby 2.7 behaviour that we are able to
        // provide a single Range argument instead of 2 Comparables.

        if (!Opal.is_a(min, Opal.Range)) {
          $Kernel.$raise($$$('TypeError'), "wrong argument type " + (min.$class()) + " (expected Range)");
        }

        excl = min.excl;
        max = min.end;
        min = min.begin;

        if (max !== nil && excl) {
          $Kernel.$raise($$$('ArgumentError'), "cannot clamp with an exclusive range");
        }
      }

      if (min !== nil && max !== nil && cmp_or_fail(min, max) > 0) {
        $Kernel.$raise($$$('ArgumentError'), "min argument must be smaller than max argument");
      }

      if (min !== nil) {
        c = cmp_or_fail(self, min);

        if (c == 0) return self;
        if (c < 0) return min;
      }

      if (max !== nil) {
        c = cmp_or_fail(self, max);

        if (c > 0) return max;
      }

      return self;
    }, -2);
  })('::')
};

Opal.modules["corelib/regexp"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $coerce_to = Opal.coerce_to, $prop = Opal.prop, $freeze = Opal.freeze, $klass = Opal.klass, $const_set = Opal.const_set, $send2 = Opal.send2, $find_super = Opal.find_super, $def = Opal.def, $truthy = Opal.truthy, $gvars = Opal.gvars, $slice = Opal.slice, $Kernel = Opal.Kernel, $Opal = Opal.Opal, $alias = Opal.alias, $send = Opal.send, $hash2 = Opal.hash2, $rb_plus = Opal.rb_plus, $ensure_kwargs = Opal.ensure_kwargs, $rb_ge = Opal.rb_ge, $to_a = Opal.to_a, $eqeqeq = Opal.eqeqeq, $rb_minus = Opal.rb_minus, $return_ivar = Opal.return_ivar, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('nil?,[],raise,escape,options,to_str,new,join,coerce_to!,!,match,coerce_to?,begin,frozen?,uniq,map,scan,source,to_proc,transform_values,group_by,each_with_index,+,last,=~,==,attr_reader,>=,length,is_a?,include?,names,regexp,named_captures,===,captures,-,inspect,empty?,each,to_a');
  
  $klass('::', $$$('StandardError'), 'RegexpError');
  (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Regexp');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting);

    
    $const_set(self, 'IGNORECASE', 1);
    $const_set(self, 'EXTENDED', 2);
    $const_set(self, 'MULTILINE', 4);
    Opal.prop(self.$$prototype, '$$is_regexp', true);
    (function(self, $parent_nesting) {
      var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting);

      
      
      $def(self, '$allocate', function $$allocate() {
        var $yield = $$allocate.$$p || nil, self = this, allocated = nil;

        $$allocate.$$p = null;
        
        allocated = $send2(self, $find_super(self, 'allocate', $$allocate, false, true), 'allocate', [], $yield);
        allocated.uninitialized = true;
        return allocated;
      });
      
      $def(self, '$escape', function $$escape(string) {
        
        return Opal.escape_regexp(string);
      });
      
      $def(self, '$last_match', function $$last_match(n) {
                if ($gvars["~"] == null) $gvars["~"] = nil;

        
        if (n == null) n = nil;
        if ($truthy(n['$nil?']())) {
          return $gvars["~"]
        } else if ($truthy($gvars["~"])) {
          return $gvars["~"]['$[]'](n)
        } else {
          return nil
        }      }, -1);
      
      $def(self, '$union', function $$union($a) {
        var $post_args, parts, self = this;

        
        $post_args = $slice(arguments);
        parts = $post_args;
        
        var is_first_part_array, quoted_validated, part, options, each_part_options;
        if (parts.length == 0) {
          return /(?!)/;
        }
        // return fast if there's only one element
        if (parts.length == 1 && parts[0].$$is_regexp) {
          return parts[0];
        }
        // cover the 2 arrays passed as arguments case
        is_first_part_array = parts[0].$$is_array;
        if (parts.length > 1 && is_first_part_array) {
          $Kernel.$raise($$$('TypeError'), "no implicit conversion of Array into String");
        }
        // deal with splat issues (related to https://github.com/opal/opal/issues/858)
        if (is_first_part_array) {
          parts = parts[0];
        }
        options = undefined;
        quoted_validated = [];
        for (var i=0; i < parts.length; i++) {
          part = parts[i];
          if (part.$$is_string) {
            quoted_validated.push(self.$escape(part));
          }
          else if (part.$$is_regexp) {
            each_part_options = (part).$options();
            if (options != undefined && options != each_part_options) {
              $Kernel.$raise($$$('TypeError'), "All expressions must use the same options");
            }
            options = each_part_options;
            quoted_validated.push('('+part.source+')');
          }
          else {
            quoted_validated.push(self.$escape((part).$to_str()));
          }
        }
        return self.$new((quoted_validated).$join("|"), options);
      }, -1);
      
      $def(self, '$new', function $new$1(regexp, options) {
        
        if (regexp.$$is_regexp) {
          return new RegExp(regexp);
        }

        regexp = $Opal['$coerce_to!'](regexp, $$$('String'), "to_str");

        if (regexp.charAt(regexp.length - 1) === '\\' && regexp.charAt(regexp.length - 2) !== '\\') {
          $Kernel.$raise($$$('RegexpError'), "too short escape sequence: /" + (regexp) + "/");
        }

        regexp = regexp.replace('\\A', '^').replace('\\z', '$');

        if (options === undefined || options['$!']()) {
          return new RegExp(regexp);
        }

        if (options.$$is_number) {
          var temp = '';
          if ($$('IGNORECASE') & options) { temp += 'i'; }
          if ($$('MULTILINE')  & options) { temp += 'm'; }
          options = temp;
        }
        else {
          options = 'i';
        }

        return new RegExp(regexp, options);
      }, -2);
      $alias(self, "compile", "new");
      return $alias(self, "quote", "escape");
    })(Opal.get_singleton_class(self), $nesting);
    
    $def(self, '$==', function $Regexp_$eq_eq$2(other) {
      var self = this;

      return other instanceof RegExp && self.toString() === other.toString();
    });
    
    $def(self, '$===', function $Regexp_$eq_eq_eq$3(string) {
      var self = this;

      return self.$match($Opal['$coerce_to?'](string, $$$('String'), "to_str")) !== nil
    });
    
    $def(self, '$=~', function $Regexp_$eq_tilde$4(string) {
      var self = this, $ret_or_1 = nil;
      if ($gvars["~"] == null) $gvars["~"] = nil;

      if ($truthy(($ret_or_1 = self.$match(string)))) {
        return $gvars["~"].$begin(0)
      } else {
        return $ret_or_1
      }
    });
    
    $def(self, '$freeze', function $$freeze() {
      var self = this;

      
      if ($truthy(self['$frozen?']())) {
        return self
      }      
      if (!self.hasOwnProperty('$$g')) { $prop(self, '$$g', null); }
      if (!self.hasOwnProperty('$$gm')) { $prop(self, '$$gm', null); }

      return $freeze(self);
    });
    
    $def(self, '$inspect', function $$inspect() {
      var self = this;

      
      var regexp_format = /^\/(.*)\/([^\/]*)$/;
      var value = self.toString();
      var matches = regexp_format.exec(value);
      if (matches) {
        var regexp_pattern = matches[1];
        var regexp_flags = matches[2];
        var chars = regexp_pattern.split('');
        var chars_length = chars.length;
        var char_escaped = false;
        var regexp_pattern_escaped = '';
        for (var i = 0; i < chars_length; i++) {
          var current_char = chars[i];
          if (!char_escaped && current_char == '/') {
            regexp_pattern_escaped = regexp_pattern_escaped.concat('\\');
          }
          regexp_pattern_escaped = regexp_pattern_escaped.concat(current_char);
          if (current_char == '\\') {
            if (char_escaped) {
              // does not over escape
              char_escaped = false;
            } else {
              char_escaped = true;
            }
          } else {
            char_escaped = false;
          }
        }
        return '/' + regexp_pattern_escaped + '/' + regexp_flags;
      } else {
        return value;
      }
    
    });
    
    $def(self, '$match', function $$match(string, pos) {
      var block = $$match.$$p || nil, self = this;
      if ($gvars["~"] == null) $gvars["~"] = nil;

      $$match.$$p = null;
      
      if (self.uninitialized) {
        $Kernel.$raise($$$('TypeError'), "uninitialized Regexp");
      }

      if (pos === undefined) {
        if (string === nil) return ($gvars["~"] = nil);
        var m = self.exec($coerce_to(string, $$$('String'), 'to_str'));
        if (m) {
          ($gvars["~"] = $$$('MatchData').$new(self, m));
          return block === nil ? $gvars["~"] : Opal.yield1(block, $gvars["~"]);
        } else {
          return ($gvars["~"] = nil);
        }
      }

      pos = $coerce_to(pos, $$$('Integer'), 'to_int');

      if (string === nil) {
        return ($gvars["~"] = nil);
      }

      string = $coerce_to(string, $$$('String'), 'to_str');

      if (pos < 0) {
        pos += string.length;
        if (pos < 0) {
          return ($gvars["~"] = nil);
        }
      }

      // global RegExp maintains state, so not using self/this
      var md, re = Opal.global_regexp(self);

      while (true) {
        md = re.exec(string);
        if (md === null) {
          return ($gvars["~"] = nil);
        }
        if (md.index >= pos) {
          ($gvars["~"] = $$$('MatchData').$new(re, md));
          return block === nil ? $gvars["~"] : Opal.yield1(block, $gvars["~"]);
        }
        re.lastIndex = md.index + 1;
      }
    }, -2);
    
    $def(self, '$match?', function $Regexp_match$ques$5(string, pos) {
      var self = this;
      
      if (self.uninitialized) {
        $Kernel.$raise($$$('TypeError'), "uninitialized Regexp");
      }

      if (pos === undefined) {
        return string === nil ? false : self.test($coerce_to(string, $$$('String'), 'to_str'));
      }

      pos = $coerce_to(pos, $$$('Integer'), 'to_int');

      if (string === nil) {
        return false;
      }

      string = $coerce_to(string, $$$('String'), 'to_str');

      if (pos < 0) {
        pos += string.length;
        if (pos < 0) {
          return false;
        }
      }

      // global RegExp maintains state, so not using self/this
      var md, re = Opal.global_regexp(self);

      md = re.exec(string);
      if (md === null || md.index < pos) {
        return false;
      } else {
        return true;
      }
    }, -2);
    
    $def(self, '$names', function $$names() {
      var self = this;

      return $send(self.$source().$scan(/\(?<(\w+)>/, $hash2(["no_matchdata"], {"no_matchdata": true})), 'map', [], "first".$to_proc()).$uniq()
    });
    
    $def(self, '$named_captures', function $$named_captures() {
      var self = this;

      return $send($send($send(self.$source().$scan(/\(?<(\w+)>/, $hash2(["no_matchdata"], {"no_matchdata": true})), 'map', [], "first".$to_proc()).$each_with_index(), 'group_by', [], "first".$to_proc()), 'transform_values', [], function $$6(i){
        
        if (i == null) i = nil;
        return $send(i, 'map', [], function $$7(j){
          
          if (j == null) j = nil;
          return $rb_plus(j.$last(), 1);});})
    });
    
    $def(self, '$~', function $Regexp_$$8() {
      var self = this;
      if ($gvars._ == null) $gvars._ = nil;

      return self['$=~']($gvars._)
    });
    
    $def(self, '$source', function $$source() {
      var self = this;

      return self.source;
    });
    
    $def(self, '$options', function $$options() {
      var self = this;

      
      if (self.uninitialized) {
        $Kernel.$raise($$$('TypeError'), "uninitialized Regexp");
      }
      var result = 0;
      // should be supported in IE6 according to https://msdn.microsoft.com/en-us/library/7f5z26w4(v=vs.94).aspx
      if (self.multiline) {
        result |= $$('MULTILINE');
      }
      if (self.ignoreCase) {
        result |= $$('IGNORECASE');
      }
      return result;
    
    });
    
    $def(self, '$casefold?', function $Regexp_casefold$ques$9() {
      var self = this;

      return self.ignoreCase;
    });
    $alias(self, "eql?", "==");
    return $alias(self, "to_s", "source");
  })('::', RegExp, $nesting);
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'MatchData');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting), $proto = self.$$prototype;

    $proto.matches = nil;
    
    self.$attr_reader("post_match", "pre_match", "regexp", "string");
    
    $def(self, '$initialize', function $$initialize(regexp, match_groups, $kwargs) {
      var no_matchdata, self = this;

      
      $kwargs = $ensure_kwargs($kwargs);
      
      no_matchdata = $kwargs.$$smap["no_matchdata"];if (no_matchdata == null) no_matchdata = false;
      if (!$truthy(no_matchdata)) {
        $gvars["~"] = self;
      }      self.regexp = regexp;
      self.begin = match_groups.index;
      self.string = match_groups.input;
      self.pre_match = match_groups.input.slice(0, match_groups.index);
      self.post_match = match_groups.input.slice(match_groups.index + match_groups[0].length);
      self.matches = [];
      
      for (var i = 0, length = match_groups.length; i < length; i++) {
        var group = match_groups[i];

        if (group == null) {
          self.matches.push(nil);
        }
        else {
          self.matches.push(group);
        }
      }
    }, -3);
    
    $def(self, '$match', function $$match(idx) {
      var self = this, match = nil;

      if ($truthy((match = self['$[]'](idx)))) {
        return match
      } else if (($truthy(idx['$is_a?']($$('Integer'))) && ($truthy($rb_ge(idx, self.$length()))))) {
        return $Kernel.$raise($$$('IndexError'), "index " + (idx) + " out of matches")
      } else {
        return nil
      }
    });
    
    $def(self, '$match_length', function $$match_length(idx) {
      var $a, self = this;

      return ($a = self.$match(idx), ($a === nil || $a == null) ? nil : $a.$length())
    });
    
    $def(self, '$[]', function $MatchData_$$$10($a) {
      var $post_args, args, self = this;

      
      $post_args = $slice(arguments);
      args = $post_args;
      
      if (args[0].$$is_string) {
        if (self.$regexp().$names()['$include?'](args['$[]'](0))['$!']()) {
          $Kernel.$raise($$$('IndexError'), "undefined group name reference: " + (args['$[]'](0)));
        }
        return self.$named_captures()['$[]'](args['$[]'](0))
      }
      else {
        return $send(self.matches, '[]', $to_a(args))
      }
    }, -1);
    
    $def(self, '$offset', function $$offset(n) {
      var self = this;

      
      if (n !== 0) {
        $Kernel.$raise($$$('ArgumentError'), "MatchData#offset only supports 0th element");
      }
      return [self.begin, self.begin + self.matches[n].length];
    
    });
    
    $def(self, '$==', function $MatchData_$eq_eq$11(other) {
      var self = this, $ret_or_1 = nil, $ret_or_2 = nil, $ret_or_3 = nil, $ret_or_4 = nil;

      
      if (!$eqeqeq($$$('MatchData'), other)) {
        return false
      }      if ($truthy(($ret_or_1 = ($truthy(($ret_or_2 = ($truthy(($ret_or_3 = ($truthy(($ret_or_4 = self.string == other.string)) ? (self.regexp.toString() == other.regexp.toString()) : ($ret_or_4)))) ? (self.pre_match == other.pre_match) : ($ret_or_3)))) ? (self.post_match == other.post_match) : ($ret_or_2))))) {
        return self.begin == other.begin;
      } else {
        return $ret_or_1
      }    });
    
    $def(self, '$begin', function $$begin(n) {
      var self = this;

      
      if (n !== 0) {
        $Kernel.$raise($$$('ArgumentError'), "MatchData#begin only supports 0th element");
      }
      return self.begin;
    
    });
    
    $def(self, '$end', function $$end(n) {
      var self = this;

      
      if (n !== 0) {
        $Kernel.$raise($$$('ArgumentError'), "MatchData#end only supports 0th element");
      }
      return self.begin + self.matches[n].length;
    
    });
    
    $def(self, '$captures', function $$captures() {
      var self = this;

      return self.matches.slice(1)
    });
    
    $def(self, '$named_captures', function $$named_captures() {
      var self = this, matches = nil;

      
      matches = self.$captures();
      return $send(self.$regexp().$named_captures(), 'transform_values', [], function $$12(i){
        
        if (i == null) i = nil;
        return matches['$[]']($rb_minus(i.$last(), 1));});
    });
    
    $def(self, '$names', function $$names() {
      var self = this;

      return self.$regexp().$names()
    });
    
    $def(self, '$inspect', function $$inspect() {
      var self = this;

      
      var str = "#<MatchData " + (self.matches[0]).$inspect();

      if (self.$regexp().$names()['$empty?']()) {
        for (var i = 1, length = self.matches.length; i < length; i++) {
          str += " " + i + ":" + (self.matches[i]).$inspect();
        }
      }
      else {
        $send(self.$named_captures(), 'each', [], function $$13(k, v){
        
        if (k == null) k = nil;
        if (v == null) v = nil;
        return                str += " " + k + ":" + v.$inspect();});
      }

      return str + ">";
    
    });
    
    $def(self, '$length', function $$length() {
      var self = this;

      return self.matches.length
    });
    
    $def(self, '$to_a', $return_ivar("matches"));
    
    $def(self, '$to_s', function $$to_s() {
      var self = this;

      return self.matches[0]
    });
    
    $def(self, '$values_at', function $$values_at($a) {
      var $post_args, args, self = this;

      
      $post_args = $slice(arguments);
      args = $post_args;
      
      var i, a, index, values = [];

      for (i = 0; i < args.length; i++) {

        if (args[i].$$is_range) {
          a = (args[i]).$to_a();
          a.unshift(i, 1);
          Array.prototype.splice.apply(args, a);
        }

        index = $Opal['$coerce_to!'](args[i], $$$('Integer'), "to_int");

        if (index < 0) {
          index += self.matches.length;
          if (index < 0) {
            values.push(nil);
            continue;
          }
        }

        values.push(self.matches[index]);
      }

      return values;
    }, -1);
    $alias(self, "eql?", "==");
    return $alias(self, "size", "length");
  })($nesting[0], null, $nesting);
};

Opal.modules["corelib/string"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $coerce_to = Opal.coerce_to, $respond_to = Opal.respond_to, $global_multiline_regexp = Opal.global_multiline_regexp, $prop = Opal.prop, $klass = Opal.klass, $def = Opal.def, $Opal = Opal.Opal, $defs = Opal.defs, $slice = Opal.slice, $send = Opal.send, $to_a = Opal.to_a, $extract_kwargs = Opal.extract_kwargs, $ensure_kwargs = Opal.ensure_kwargs, $eqeqeq = Opal.eqeqeq, $Kernel = Opal.Kernel, $truthy = Opal.truthy, $gvars = Opal.gvars, $rb_divide = Opal.rb_divide, $rb_plus = Opal.rb_plus, $eqeq = Opal.eqeq, $hash2 = Opal.hash2, $alias = Opal.alias, $const_set = Opal.const_set, self = Opal.top, $nesting = [], $$ = Opal.$r($nesting), nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('require,include,coerce_to?,initialize,===,format,raise,respond_to?,to_s,to_str,<=>,==,=~,new,force_encoding,casecmp,empty?,ljust,ceil,/,+,rjust,floor,coerce_to!,nil?,class,copy_singleton_methods,initialize_clone,initialize_dup,enum_for,chomp,[],to_i,length,each_line,to_proc,to_a,match,match?,captures,proc,succ,escape,include?,upcase,unicode_normalize,dup,__id__,next,intern,pristine');
  
  self.$require("corelib/comparable");
  self.$require("corelib/regexp");
  (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'String');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting);

    
    self.$include($$$('Comparable'));
    
    Opal.prop(self.$$prototype, '$$is_string', true);
    
    $def(self, '$__id__', function $$__id__() {
      var self = this;

      return self.toString();
    });
    $defs(self, '$try_convert', function $$try_convert(what) {
      
      return $Opal['$coerce_to?'](what, $$$('String'), "to_str")
    });
    $defs(self, '$new', function $String_new$1($a) {
      var $post_args, args, self = this;

      
      $post_args = $slice(arguments);
      args = $post_args;
      
      var str = args[0] || "";
      var opts = args[args.length-1];
      str = $coerce_to(str, $$$('String'), 'to_str');
      if (opts && opts.$$is_hash) {
        if (opts.$$smap.encoding) str = str.$force_encoding(opts.$$smap.encoding);
      }
      str = new self.$$constructor(str);
      if (!str.$initialize.$$pristine) $send((str), 'initialize', $to_a(args));
      return str;
    }, -1);
    
    $def(self, '$initialize', function $$initialize($a, $b) {
      var $post_args, $kwargs;

      
      $post_args = $slice(arguments);
      $kwargs = $extract_kwargs($post_args);
      $kwargs = $ensure_kwargs($kwargs);
      
      if ($post_args.length > 0) $post_args.shift();      
      $kwargs.$$smap["encoding"];      
      $kwargs.$$smap["capacity"];      return nil;
    }, -1);
    
    $def(self, '$%', function $String_$percent$2(data) {
      var self = this;

      if ($eqeqeq($$$('Array'), data)) {
        return $send(self, 'format', [self].concat($to_a(data)))
      } else {
        return self.$format(self, data)
      }
    });
    
    $def(self, '$*', function $String_$$3(count) {
      var self = this;

      
      count = $coerce_to(count, $$$('Integer'), 'to_int');

      if (count < 0) {
        $Kernel.$raise($$$('ArgumentError'), "negative argument");
      }

      if (count === 0) {
        return '';
      }

      var result = '',
          string = self.toString();

      // All credit for the bit-twiddling magic code below goes to Mozilla
      // polyfill implementation of String.prototype.repeat() posted here:
      // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/repeat

      if (string.length * count >= 1 << 28) {
        $Kernel.$raise($$$('RangeError'), "multiply count must not overflow maximum string size");
      }

      for (;;) {
        if ((count & 1) === 1) {
          result += string;
        }
        count >>>= 1;
        if (count === 0) {
          break;
        }
        string += string;
      }

      return result;
    
    });
    
    $def(self, '$+', function $String_$plus$4(other) {
      var self = this;

      
      other = $coerce_to(other, $$$('String'), 'to_str');
      
      if (other == "" && self.$$class === Opal.String) return self;
      if (self == "" && other.$$class === Opal.String) return other;
      var out = self + other;
      if (self.encoding === out.encoding && other.encoding === out.encoding) return out;
      if (self.encoding.name === "UTF-8" || other.encoding.name === "UTF-8") return out;
      return Opal.enc(out, self.encoding);
    });
    
    $def(self, '$<=>', function $String_$lt_eq_gt$5(other) {
      var self = this;

      if ($truthy(other['$respond_to?']("to_str"))) {
        
        other = other.$to_str().$to_s();
        return self > other ? 1 : (self < other ? -1 : 0);      } else {
        
        var cmp = other['$<=>'](self);

        if (cmp === nil) {
          return nil;
        }
        else {
          return cmp > 0 ? -1 : (cmp < 0 ? 1 : 0);
        }
      
      }
    });
    
    $def(self, '$==', function $String_$eq_eq$6(other) {
      var self = this;

      
      if (other.$$is_string) {
        return self.toString() === other.toString();
      }
      if ($respond_to(other, '$to_str')) {
        return other['$=='](self);
      }
      return false;
    
    });
    
    $def(self, '$=~', function $String_$eq_tilde$7(other) {
      var self = this;

      
      if (other.$$is_string) {
        $Kernel.$raise($$$('TypeError'), "type mismatch: String given");
      }

      return other['$=~'](self);
    
    });
    
    $def(self, '$[]', function $String_$$$8(index, length) {
      var self = this;
      
      var size = self.length, exclude, range;

      if (index.$$is_range) {
        exclude = index.excl;
        range   = index;
        length  = index.end === nil ? -1 : $coerce_to(index.end, $$$('Integer'), 'to_int');
        index   = index.begin === nil ? 0 : $coerce_to(index.begin, $$$('Integer'), 'to_int');

        if (Math.abs(index) > size) {
          return nil;
        }

        if (index < 0) {
          index += size;
        }

        if (length < 0) {
          length += size;
        }

        if (!exclude || range.end === nil) {
          length += 1;
        }

        length = length - index;

        if (length < 0) {
          length = 0;
        }

        return self.substr(index, length);
      }


      if (index.$$is_string) {
        if (length != null) {
          $Kernel.$raise($$$('TypeError'));
        }
        return self.indexOf(index) !== -1 ? index : nil;
      }


      if (index.$$is_regexp) {
        var match = self.match(index);

        if (match === null) {
          ($gvars["~"] = nil);
          return nil;
        }

        ($gvars["~"] = $$$('MatchData').$new(index, match));

        if (length == null) {
          return match[0];
        }

        length = $coerce_to(length, $$$('Integer'), 'to_int');

        if (length < 0 && -length < match.length) {
          return match[length += match.length];
        }

        if (length >= 0 && length < match.length) {
          return match[length];
        }

        return nil;
      }


      index = $coerce_to(index, $$$('Integer'), 'to_int');

      if (index < 0) {
        index += size;
      }

      if (length == null) {
        if (index >= size || index < 0) {
          return nil;
        }
        return self.substr(index, 1);
      }

      length = $coerce_to(length, $$$('Integer'), 'to_int');

      if (length < 0) {
        return nil;
      }

      if (index > size || index < 0) {
        return nil;
      }

      return self.substr(index, length);
    }, -2);
    
    $def(self, '$b', function $$b() {
      var self = this;

      return (new String(self)).$force_encoding("binary")
    });
    
    $def(self, '$capitalize', function $$capitalize() {
      var self = this;

      return self.charAt(0).toUpperCase() + self.substr(1).toLowerCase();
    });
    
    $def(self, '$casecmp', function $$casecmp(other) {
      var self = this;

      
      if (!$truthy(other['$respond_to?']("to_str"))) {
        return nil
      }      other = ($coerce_to(other, $$$('String'), 'to_str')).$to_s();
      
      var ascii_only = /^[\x00-\x7F]*$/;
      if (ascii_only.test(self) && ascii_only.test(other)) {
        self = self.toLowerCase();
        other = other.toLowerCase();
      }
      return self['$<=>'](other);
    });
    
    $def(self, '$casecmp?', function $String_casecmp$ques$9(other) {
      var self = this;

      
      var cmp = self.$casecmp(other);
      if (cmp === nil) {
        return nil;
      } else {
        return cmp === 0;
      }
    
    });
    
    $def(self, '$center', function $$center(width, padstr) {
      var self = this;

      
      if (padstr == null) padstr = " ";
      width = $coerce_to(width, $$$('Integer'), 'to_int');
      padstr = ($coerce_to(padstr, $$$('String'), 'to_str')).$to_s();
      if ($truthy(padstr['$empty?']())) {
        $Kernel.$raise($$$('ArgumentError'), "zero width padding");
      }      if ($truthy(width <= self.length)) {
        return self
      }      
      var ljustified = self.$ljust($rb_divide($rb_plus(width, self.length), 2).$ceil(), padstr),
          rjustified = self.$rjust($rb_divide($rb_plus(width, self.length), 2).$floor(), padstr);

      return rjustified + ljustified.slice(self.length);
    }, -2);
    
    $def(self, '$chomp', function $$chomp(separator) {
      var self = this;
      if ($gvars["/"] == null) $gvars["/"] = nil;

      
      if (separator == null) separator = $gvars["/"];
      if ($truthy(separator === nil || self.length === 0)) {
        return self
      }      separator = $Opal['$coerce_to!'](separator, $$$('String'), "to_str").$to_s();
      
      var result;

      if (separator === "\n") {
        result = self.replace(/\r?\n?$/, '');
      }
      else if (separator === "") {
        result = self.replace(/(\r?\n)+$/, '');
      }
      else if (self.length >= separator.length) {
        var tail = self.substr(self.length - separator.length, separator.length);

        if (tail === separator) {
          result = self.substr(0, self.length - separator.length);
        }
      }

      if (result != null) {
        return result;
      }
      return self;
    }, -1);
    
    $def(self, '$chop', function $$chop() {
      var self = this;

      
      var length = self.length, result;

      if (length <= 1) {
        result = "";
      } else if (self.charAt(length - 1) === "\n" && self.charAt(length - 2) === "\r") {
        result = self.substr(0, length - 2);
      } else {
        result = self.substr(0, length - 1);
      }

      return result;
    
    });
    
    $def(self, '$chr', function $$chr() {
      var self = this;

      return self.charAt(0);
    });
    
    $def(self, '$clone', function $$clone($kwargs) {
      var freeze, self = this, copy = nil;

      
      $kwargs = $ensure_kwargs($kwargs);
      
      freeze = $kwargs.$$smap["freeze"];if (freeze == null) freeze = nil;
      if (!(($truthy(freeze['$nil?']()) || ($eqeq(freeze, true))) || ($eqeq(freeze, false)))) {
        self.$raise($$('ArgumentError'), "unexpected value for freeze: " + (freeze.$class()));
      }      copy = new String(self);
      copy.$copy_singleton_methods(self);
      copy.$initialize_clone(self, $hash2(["freeze"], {"freeze": freeze}));
      if ($eqeq(freeze, true)) {
        if (!copy.$$frozen) { copy.$$frozen = true; }
      } else if ($truthy(freeze['$nil?']())) {
        if (self.$$frozen) { copy.$$frozen = true; }
      }      return copy;
    }, -1);
    
    $def(self, '$dup', function $$dup() {
      var self = this, copy = nil;

      
      copy = new String(self);
      copy.$initialize_dup(self);
      return copy;
    });
    
    $def(self, '$count', function $$count($a) {
      var $post_args, sets, self = this;

      
      $post_args = $slice(arguments);
      sets = $post_args;
      
      if (sets.length === 0) {
        $Kernel.$raise($$$('ArgumentError'), "ArgumentError: wrong number of arguments (0 for 1+)");
      }
      var char_class = char_class_from_char_sets(sets);
      if (char_class === null) {
        return 0;
      }
      return self.length - self.replace(new RegExp(char_class, 'g'), '').length;
    }, -1);
    
    $def(self, '$delete', function $String_delete$10($a) {
      var $post_args, sets, self = this;

      
      $post_args = $slice(arguments);
      sets = $post_args;
      
      if (sets.length === 0) {
        $Kernel.$raise($$$('ArgumentError'), "ArgumentError: wrong number of arguments (0 for 1+)");
      }
      var char_class = char_class_from_char_sets(sets);
      if (char_class === null) {
        return self;
      }
      return self.replace(new RegExp(char_class, 'g'), '');
    }, -1);
    
    $def(self, '$delete_prefix', function $$delete_prefix(prefix) {
      var self = this;

      
      if (!prefix.$$is_string) {
        prefix = $coerce_to(prefix, $$$('String'), 'to_str');
      }

      if (self.slice(0, prefix.length) === prefix) {
        return self.slice(prefix.length);
      } else {
        return self;
      }
    
    });
    
    $def(self, '$delete_suffix', function $$delete_suffix(suffix) {
      var self = this;

      
      if (!suffix.$$is_string) {
        suffix = $coerce_to(suffix, $$$('String'), 'to_str');
      }

      if (self.slice(self.length - suffix.length) === suffix) {
        return self.slice(0, self.length - suffix.length);
      } else {
        return self;
      }
    
    });
    
    $def(self, '$downcase', function $$downcase() {
      var self = this;

      return self.toLowerCase();
    });
    
    $def(self, '$each_line', function $$each_line($a, $b) {
      var block = $$each_line.$$p || nil, $post_args, $kwargs, separator, chomp, self = this;
      if ($gvars["/"] == null) $gvars["/"] = nil;

      $$each_line.$$p = null;
      $post_args = $slice(arguments);
      $kwargs = $extract_kwargs($post_args);
      $kwargs = $ensure_kwargs($kwargs);
      
      if ($post_args.length > 0) separator = $post_args.shift();if (separator == null) separator = $gvars["/"];
      
      chomp = $kwargs.$$smap["chomp"];if (chomp == null) chomp = false;
      if (!(block !== nil)) {
        return self.$enum_for("each_line", separator, $hash2(["chomp"], {"chomp": chomp}))
      }      
      if (separator === nil) {
        Opal.yield1(block, self);

        return self;
      }

      separator = $coerce_to(separator, $$$('String'), 'to_str');

      var a, i, n, length, chomped, trailing, splitted, value;

      if (separator.length === 0) {
        for (a = self.split(/((?:\r?\n){2})(?:(?:\r?\n)*)/), i = 0, n = a.length; i < n; i += 2) {
          if (a[i] || a[i + 1]) {
            value = (a[i] || "") + (a[i + 1] || "");
            if (chomp) {
              value = (value).$chomp("\n");
            }
            Opal.yield1(block, value);
          }
        }

        return self;
      }

      chomped  = self.$chomp(separator);
      trailing = self.length != chomped.length;
      splitted = chomped.split(separator);

      for (i = 0, length = splitted.length; i < length; i++) {
        value = splitted[i];
        if (i < length - 1 || trailing) {
          value += separator;
        }
        if (chomp) {
          value = (value).$chomp(separator);
        }
        Opal.yield1(block, value);
      }
      return self;
    }, -1);
    
    $def(self, '$empty?', function $String_empty$ques$11() {
      var self = this;

      return self.length === 0;
    });
    
    $def(self, '$end_with?', function $String_end_with$ques$12($a) {
      var $post_args, suffixes, self = this;

      
      $post_args = $slice(arguments);
      suffixes = $post_args;
      
      for (var i = 0, length = suffixes.length; i < length; i++) {
        var suffix = $coerce_to(suffixes[i], $$$('String'), 'to_str').$to_s();

        if (self.length >= suffix.length &&
            self.substr(self.length - suffix.length, suffix.length) == suffix) {
          return true;
        }
      }
      return false;
    }, -1);
    
    $def(self, '$gsub', function $$gsub(pattern, replacement) {
      var block = $$gsub.$$p || nil, self = this;

      $$gsub.$$p = null;
      
      if (replacement === undefined && block === nil) {
        return self.$enum_for("gsub", pattern);
      }

      var result = '', match_data = nil, index = 0, match, _replacement;

      if (pattern.$$is_regexp) {
        pattern = $global_multiline_regexp(pattern);
      } else {
        pattern = $coerce_to(pattern, $$$('String'), 'to_str');
        pattern = new RegExp(pattern.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'gm');
      }

      var lastIndex;
      while (true) {
        match = pattern.exec(self);

        if (match === null) {
          ($gvars["~"] = nil);
          result += self.slice(index);
          break;
        }

        match_data = $$$('MatchData').$new(pattern, match);

        if (replacement === undefined) {
          lastIndex = pattern.lastIndex;
          _replacement = block(match[0]);
          pattern.lastIndex = lastIndex; // save and restore lastIndex
        }
        else if (replacement.$$is_hash) {
          _replacement = (replacement)['$[]'](match[0]).$to_s();
        }
        else {
          if (!replacement.$$is_string) {
            replacement = $coerce_to(replacement, $$$('String'), 'to_str');
          }
          _replacement = replacement.replace(/([\\]+)([0-9+&`'])/g, function (original, slashes, command) {
            if (slashes.length % 2 === 0) {
              return original;
            }
            switch (command) {
            case "+":
              for (var i = match.length - 1; i > 0; i--) {
                if (match[i] !== undefined) {
                  return slashes.slice(1) + match[i];
                }
              }
              return '';
            case "&": return slashes.slice(1) + match[0];
            case "`": return slashes.slice(1) + self.slice(0, match.index);
            case "'": return slashes.slice(1) + self.slice(match.index + match[0].length);
            default:  return slashes.slice(1) + (match[command] || '');
            }
          }).replace(/\\\\/g, '\\');
        }

        if (pattern.lastIndex === match.index) {
          result += (self.slice(index, match.index) + _replacement + (self[match.index] || ""));
          pattern.lastIndex += 1;
        }
        else {
          result += (self.slice(index, match.index) + _replacement);
        }
        index = pattern.lastIndex;
      }

      ($gvars["~"] = match_data);
      return result;
    }, -2);
    
    $def(self, '$hash', function $$hash() {
      var self = this;

      return self.toString();
    });
    
    $def(self, '$hex', function $$hex() {
      var self = this;

      return self.$to_i(16)
    });
    
    $def(self, '$include?', function $String_include$ques$13(other) {
      var self = this;

      
      if (!other.$$is_string) {
        other = $coerce_to(other, $$$('String'), 'to_str');
      }
      return self.indexOf(other) !== -1;
    
    });
    
    $def(self, '$index', function $$index(search, offset) {
      var self = this;
      
      var index,
          match,
          regex;

      if (offset === undefined) {
        offset = 0;
      } else {
        offset = $coerce_to(offset, $$$('Integer'), 'to_int');
        if (offset < 0) {
          offset += self.length;
          if (offset < 0) {
            return nil;
          }
        }
      }

      if (search.$$is_regexp) {
        regex = $global_multiline_regexp(search);
        while (true) {
          match = regex.exec(self);
          if (match === null) {
            ($gvars["~"] = nil);
            index = -1;
            break;
          }
          if (match.index >= offset) {
            ($gvars["~"] = $$$('MatchData').$new(regex, match));
            index = match.index;
            break;
          }
          regex.lastIndex = match.index + 1;
        }
      } else {
        search = $coerce_to(search, $$$('String'), 'to_str');
        if (search.length === 0 && offset > self.length) {
          index = -1;
        } else {
          index = self.indexOf(search, offset);
        }
      }

      return index === -1 ? nil : index;
    }, -2);
    
    $def(self, '$inspect', function $$inspect() {
      var self = this;

      
      /* eslint-disable no-misleading-character-class */
      var escapable = /[\\\"\x00-\x1f\u007F-\u009F\u0600-\u0604\u070f\u17b4\u17b5\u200c-\u200f\u2028-\u202f\u2060-\u206f\ufeff\ufff0-\uffff]/g,
          meta = {
            '\u0007': '\\a',
            '\u001b': '\\e',
            '\b': '\\b',
            '\t': '\\t',
            '\n': '\\n',
            '\f': '\\f',
            '\r': '\\r',
            '\v': '\\v',
            '"' : '\\"',
            '\\': '\\\\'
          },
          escaped = self.replace(escapable, function (chr) {
            if (meta[chr]) return meta[chr];
            chr = chr.charCodeAt(0);
            if (chr <= 0xff && (self.encoding["$binary?"]() || self.internal_encoding["$binary?"]())) {
              return '\\x' + ('00' + chr.toString(16).toUpperCase()).slice(-2);
            } else {
              return '\\u' + ('0000' + chr.toString(16).toUpperCase()).slice(-4);
            }
          });
      return '"' + escaped.replace(/\#[\$\@\{]/g, '\\$&') + '"';
      /* eslint-enable no-misleading-character-class */
    
    });
    
    $def(self, '$intern', function $$intern() {
      var self = this;

      return self.toString();
    });
    
    $def(self, '$length', function $$length() {
      var self = this;

      return self.length;
    });
    $alias(self, "size", "length");
    
    $def(self, '$lines', function $$lines($a, $b) {
      var block = $$lines.$$p || nil, $post_args, $kwargs, separator, chomp, self = this, e = nil;
      if ($gvars["/"] == null) $gvars["/"] = nil;

      $$lines.$$p = null;
      $post_args = $slice(arguments);
      $kwargs = $extract_kwargs($post_args);
      $kwargs = $ensure_kwargs($kwargs);
      
      if ($post_args.length > 0) separator = $post_args.shift();if (separator == null) separator = $gvars["/"];
      
      chomp = $kwargs.$$smap["chomp"];if (chomp == null) chomp = false;
      e = $send(self, 'each_line', [separator, $hash2(["chomp"], {"chomp": chomp})], block.$to_proc());
      if ($truthy(block)) {
        return self
      } else {
        return e.$to_a()
      }    }, -1);
    
    $def(self, '$ljust', function $$ljust(width, padstr) {
      var self = this;

      
      if (padstr == null) padstr = " ";
      width = $coerce_to(width, $$$('Integer'), 'to_int');
      padstr = ($coerce_to(padstr, $$$('String'), 'to_str')).$to_s();
      if ($truthy(padstr['$empty?']())) {
        $Kernel.$raise($$$('ArgumentError'), "zero width padding");
      }      if ($truthy(width <= self.length)) {
        return self
      }      
      var index  = -1,
          result = "";

      width -= self.length;

      while (++index < width) {
        result += padstr;
      }

      return self + result.slice(0, width);
    }, -2);
    
    $def(self, '$lstrip', function $$lstrip() {
      var self = this;

      return self.replace(/^[\u0000\s]*/, '');
    });
    
    $def(self, '$ascii_only?', function $String_ascii_only$ques$14() {
      var self = this;

      
      if (!self.encoding.ascii) return false;
      return /^[\x00-\x7F]*$/.test(self);
    
    });
    
    $def(self, '$match', function $$match(pattern, pos) {
      var block = $$match.$$p || nil, self = this;

      $$match.$$p = null;
      if (($eqeqeq($$('String'), pattern) || ($truthy(pattern['$respond_to?']("to_str"))))) {
        pattern = $$$('Regexp').$new(pattern.$to_str());
      }      if (!$eqeqeq($$$('Regexp'), pattern)) {
        $Kernel.$raise($$$('TypeError'), "wrong argument type " + (pattern.$class()) + " (expected Regexp)");
      }      return $send(pattern, 'match', [self, pos], block.$to_proc());
    }, -2);
    
    $def(self, '$match?', function $String_match$ques$15(pattern, pos) {
      var self = this;
      if (($eqeqeq($$('String'), pattern) || ($truthy(pattern['$respond_to?']("to_str"))))) {
        pattern = $$$('Regexp').$new(pattern.$to_str());
      }      if (!$eqeqeq($$$('Regexp'), pattern)) {
        $Kernel.$raise($$$('TypeError'), "wrong argument type " + (pattern.$class()) + " (expected Regexp)");
      }      return pattern['$match?'](self, pos);
    }, -2);
    
    $def(self, '$next', function $$next() {
      var self = this;

      
      var i = self.length;
      if (i === 0) {
        return '';
      }
      var result = self;
      var first_alphanum_char_index = self.search(/[a-zA-Z0-9]/);
      var carry = false;
      var code;
      while (i--) {
        code = self.charCodeAt(i);
        if ((code >= 48 && code <= 57) ||
          (code >= 65 && code <= 90) ||
          (code >= 97 && code <= 122)) {
          switch (code) {
          case 57:
            carry = true;
            code = 48;
            break;
          case 90:
            carry = true;
            code = 65;
            break;
          case 122:
            carry = true;
            code = 97;
            break;
          default:
            carry = false;
            code += 1;
          }
        } else {
          if (first_alphanum_char_index === -1) {
            if (code === 255) {
              carry = true;
              code = 0;
            } else {
              carry = false;
              code += 1;
            }
          } else {
            carry = true;
          }
        }
        result = result.slice(0, i) + String.fromCharCode(code) + result.slice(i + 1);
        if (carry && (i === 0 || i === first_alphanum_char_index)) {
          switch (code) {
          case 65:
            break;
          case 97:
            break;
          default:
            code += 1;
          }
          if (i === 0) {
            result = String.fromCharCode(code) + result;
          } else {
            result = result.slice(0, i) + String.fromCharCode(code) + result.slice(i);
          }
          carry = false;
        }
        if (!carry) {
          break;
        }
      }
      return result;
    
    });
    
    $def(self, '$oct', function $$oct() {
      var self = this;

      
      var result,
          string = self,
          radix = 8;

      if (/^\s*_/.test(string)) {
        return 0;
      }

      string = string.replace(/^(\s*[+-]?)(0[bodx]?)(.+)$/i, function (original, head, flag, tail) {
        switch (tail.charAt(0)) {
        case '+':
        case '-':
          return original;
        case '0':
          if (tail.charAt(1) === 'x' && flag === '0x') {
            return original;
          }
        }
        switch (flag) {
        case '0b':
          radix = 2;
          break;
        case '0':
        case '0o':
          radix = 8;
          break;
        case '0d':
          radix = 10;
          break;
        case '0x':
          radix = 16;
          break;
        }
        return head + tail;
      });

      result = parseInt(string.replace(/_(?!_)/g, ''), radix);
      return isNaN(result) ? 0 : result;
    
    });
    
    $def(self, '$ord', function $$ord() {
      var self = this;

      
      if (typeof self.codePointAt === "function") {
        return self.codePointAt(0);
      }
      else {
        return self.charCodeAt(0);
      }
    
    });
    
    $def(self, '$partition', function $$partition(sep) {
      var self = this;

      
      var i, m;

      if (sep.$$is_regexp) {
        m = sep.exec(self);
        if (m === null) {
          i = -1;
        } else {
          $$$('MatchData').$new(sep, m);
          sep = m[0];
          i = m.index;
        }
      } else {
        sep = $coerce_to(sep, $$$('String'), 'to_str');
        i = self.indexOf(sep);
      }

      if (i === -1) {
        return [self, '', ''];
      }

      return [
        self.slice(0, i),
        self.slice(i, i + sep.length),
        self.slice(i + sep.length)
      ];
    
    });
    
    $def(self, '$reverse', function $$reverse() {
      var self = this;

      return self.split('').reverse().join('');
    });
    
    $def(self, '$rindex', function $$rindex(search, offset) {
      var self = this;
      
      var i, m, r, _m;

      if (offset === undefined) {
        offset = self.length;
      } else {
        offset = $coerce_to(offset, $$$('Integer'), 'to_int');
        if (offset < 0) {
          offset += self.length;
          if (offset < 0) {
            return nil;
          }
        }
      }

      if (search.$$is_regexp) {
        m = null;
        r = $global_multiline_regexp(search);
        while (true) {
          _m = r.exec(self);
          if (_m === null || _m.index > offset) {
            break;
          }
          m = _m;
          r.lastIndex = m.index + 1;
        }
        if (m === null) {
          ($gvars["~"] = nil);
          i = -1;
        } else {
          $$$('MatchData').$new(r, m);
          i = m.index;
        }
      } else {
        search = $coerce_to(search, $$$('String'), 'to_str');
        i = self.lastIndexOf(search, offset);
      }

      return i === -1 ? nil : i;
    }, -2);
    
    $def(self, '$rjust', function $$rjust(width, padstr) {
      var self = this;

      
      if (padstr == null) padstr = " ";
      width = $coerce_to(width, $$$('Integer'), 'to_int');
      padstr = ($coerce_to(padstr, $$$('String'), 'to_str')).$to_s();
      if ($truthy(padstr['$empty?']())) {
        $Kernel.$raise($$$('ArgumentError'), "zero width padding");
      }      if ($truthy(width <= self.length)) {
        return self
      }      
      var chars     = Math.floor(width - self.length),
          patterns  = Math.floor(chars / padstr.length),
          result    = Array(patterns + 1).join(padstr),
          remaining = chars - result.length;

      return result + padstr.slice(0, remaining) + self;
    }, -2);
    
    $def(self, '$rpartition', function $$rpartition(sep) {
      var self = this;

      
      var i, m, r, _m;

      if (sep.$$is_regexp) {
        m = null;
        r = $global_multiline_regexp(sep);

        while (true) {
          _m = r.exec(self);
          if (_m === null) {
            break;
          }
          m = _m;
          r.lastIndex = m.index + 1;
        }

        if (m === null) {
          i = -1;
        } else {
          $$$('MatchData').$new(r, m);
          sep = m[0];
          i = m.index;
        }

      } else {
        sep = $coerce_to(sep, $$$('String'), 'to_str');
        i = self.lastIndexOf(sep);
      }

      if (i === -1) {
        return ['', '', self];
      }

      return [
        self.slice(0, i),
        self.slice(i, i + sep.length),
        self.slice(i + sep.length)
      ];
    
    });
    
    $def(self, '$rstrip', function $$rstrip() {
      var self = this;

      return self.replace(/[\s\u0000]*$/, '');
    });
    
    $def(self, '$scan', function $$scan(pattern, $kwargs) {
      var block = $$scan.$$p || nil, no_matchdata, self = this;

      $$scan.$$p = null;
      $kwargs = $ensure_kwargs($kwargs);
      
      no_matchdata = $kwargs.$$smap["no_matchdata"];if (no_matchdata == null) no_matchdata = false;
      
      var result = [],
          match_data = nil,
          match;

      if (pattern.$$is_regexp) {
        pattern = $global_multiline_regexp(pattern);
      } else {
        pattern = $coerce_to(pattern, $$$('String'), 'to_str');
        pattern = new RegExp(pattern.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'gm');
      }

      while ((match = pattern.exec(self)) != null) {
        match_data = $$$('MatchData').$new(pattern, match, $hash2(["no_matchdata"], {"no_matchdata": no_matchdata}));
        if (block === nil) {
          match.length == 1 ? result.push(match[0]) : result.push((match_data).$captures());
        } else {
          match.length == 1 ? Opal.yield1(block, match[0]) : Opal.yield1(block, (match_data).$captures());
        }
        if (pattern.lastIndex === match.index) {
          pattern.lastIndex += 1;
        }
      }

      if (!no_matchdata) ($gvars["~"] = match_data);

      return (block !== nil ? self : result);
    }, -2);
    
    $def(self, '$singleton_class', function $$singleton_class() {
      var self = this;

      return Opal.get_singleton_class(self);
    });
    
    $def(self, '$split', function $$split(pattern, limit) {
      var self = this, $ret_or_1 = nil;
      if ($gvars[";"] == null) $gvars[";"] = nil;
      
      if (self.length === 0) {
        return [];
      }

      if (limit === undefined) {
        limit = 0;
      } else {
        limit = $Opal['$coerce_to!'](limit, $$$('Integer'), "to_int");
        if (limit === 1) {
          return [self];
        }
      }

      if (pattern === undefined || pattern === nil) {
        pattern = ($truthy(($ret_or_1 = $gvars[";"])) ? ($ret_or_1) : (" "));
      }

      var result = [],
          string = self.toString(),
          index = 0,
          match,
          i, ii;

      if (pattern.$$is_regexp) {
        pattern = $global_multiline_regexp(pattern);
      } else {
        pattern = $coerce_to(pattern, $$$('String'), 'to_str').$to_s();
        if (pattern === ' ') {
          pattern = /\s+/gm;
          string = string.replace(/^\s+/, '');
        } else {
          pattern = new RegExp(pattern.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'gm');
        }
      }

      result = string.split(pattern);

      if (result.length === 1 && result[0] === string) {
        return [result[0]];
      }

      while ((i = result.indexOf(undefined)) !== -1) {
        result.splice(i, 1);
      }

      if (limit === 0) {
        while (result[result.length - 1] === '') {
          result.length -= 1;
        }
        return result;
      }

      match = pattern.exec(string);

      if (limit < 0) {
        if (match !== null && match[0] === '' && pattern.source.indexOf('(?=') === -1) {
          for (i = 0, ii = match.length; i < ii; i++) {
            result.push('');
          }
        }
        return result;
      }

      if (match !== null && match[0] === '') {
        result.splice(limit - 1, result.length - 1, result.slice(limit - 1).join(''));
        return result;
      }

      if (limit >= result.length) {
        return result;
      }

      i = 0;
      while (match !== null) {
        i++;
        index = pattern.lastIndex;
        if (i + 1 === limit) {
          break;
        }
        match = pattern.exec(string);
      }
      result.splice(limit - 1, result.length - 1, string.slice(index));
      return result;
    }, -1);
    
    $def(self, '$squeeze', function $$squeeze($a) {
      var $post_args, sets, self = this;

      
      $post_args = $slice(arguments);
      sets = $post_args;
      
      if (sets.length === 0) {
        return self.replace(/(.)\1+/g, '$1');
      }
      var char_class = char_class_from_char_sets(sets);
      if (char_class === null) {
        return self;
      }
      return self.replace(new RegExp('(' + char_class + ')\\1+', 'g'), '$1');
    }, -1);
    
    $def(self, '$start_with?', function $String_start_with$ques$16($a) {
      var $post_args, prefixes, self = this;

      
      $post_args = $slice(arguments);
      prefixes = $post_args;
      
      for (var i = 0, length = prefixes.length; i < length; i++) {
        if (prefixes[i].$$is_regexp) {
          var regexp = prefixes[i];
          var match = regexp.exec(self);

          if (match != null && match.index === 0) {
            ($gvars["~"] = $$$('MatchData').$new(regexp, match));
            return true;
          } else {
            ($gvars["~"] = nil);
          }
        } else {
          var prefix = $coerce_to(prefixes[i], $$$('String'), 'to_str').$to_s();

          if (self.indexOf(prefix) === 0) {
            return true;
          }
        }
      }

      return false;
    }, -1);
    
    $def(self, '$strip', function $$strip() {
      var self = this;

      return self.replace(/^[\s\u0000]*|[\s\u0000]*$/g, '');
    });
    
    $def(self, '$sub', function $$sub(pattern, replacement) {
      var block = $$sub.$$p || nil, self = this;

      $$sub.$$p = null;
      
      if (!pattern.$$is_regexp) {
        pattern = $coerce_to(pattern, $$$('String'), 'to_str');
        pattern = new RegExp(pattern.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
      }

      var result, match = pattern.exec(self);

      if (match === null) {
        ($gvars["~"] = nil);
        result = self.toString();
      } else {
        $$$('MatchData').$new(pattern, match);

        if (replacement === undefined) {

          if (block === nil) {
            $Kernel.$raise($$$('ArgumentError'), "wrong number of arguments (1 for 2)");
          }
          result = self.slice(0, match.index) + block(match[0]) + self.slice(match.index + match[0].length);

        } else if (replacement.$$is_hash) {

          result = self.slice(0, match.index) + (replacement)['$[]'](match[0]).$to_s() + self.slice(match.index + match[0].length);

        } else {

          replacement = $coerce_to(replacement, $$$('String'), 'to_str');

          replacement = replacement.replace(/([\\]+)([0-9+&`'])/g, function (original, slashes, command) {
            if (slashes.length % 2 === 0) {
              return original;
            }
            switch (command) {
            case "+":
              for (var i = match.length - 1; i > 0; i--) {
                if (match[i] !== undefined) {
                  return slashes.slice(1) + match[i];
                }
              }
              return '';
            case "&": return slashes.slice(1) + match[0];
            case "`": return slashes.slice(1) + self.slice(0, match.index);
            case "'": return slashes.slice(1) + self.slice(match.index + match[0].length);
            default:  return slashes.slice(1) + (match[command] || '');
            }
          }).replace(/\\\\/g, '\\');

          result = self.slice(0, match.index) + replacement + self.slice(match.index + match[0].length);
        }
      }

      return result;
    }, -2);
    
    $def(self, '$sum', function $$sum(n) {
      var self = this;

      
      if (n == null) n = 16;
      
      n = $coerce_to(n, $$$('Integer'), 'to_int');

      var result = 0,
          length = self.length,
          i = 0;

      for (; i < length; i++) {
        result += self.charCodeAt(i);
      }

      if (n <= 0) {
        return result;
      }

      return result & (Math.pow(2, n) - 1);
    }, -1);
    
    $def(self, '$swapcase', function $$swapcase() {
      var self = this;

      
      var str = self.replace(/([a-z]+)|([A-Z]+)/g, function($0,$1,$2) {
        return $1 ? $0.toUpperCase() : $0.toLowerCase();
      });

      return str;
    
    });
    
    $def(self, '$to_f', function $$to_f() {
      var self = this;

      
      if (self.charAt(0) === '_') {
        return 0;
      }

      var result = parseFloat(self.replace(/_/g, ''));

      if (isNaN(result) || result == Infinity || result == -Infinity) {
        return 0;
      }
      else {
        return result;
      }
    
    });
    
    $def(self, '$to_i', function $$to_i(base) {
      var self = this;

      
      if (base == null) base = 10;
      
      var result,
          string = self.toLowerCase(),
          radix = $coerce_to(base, $$$('Integer'), 'to_int');

      if (radix === 1 || radix < 0 || radix > 36) {
        $Kernel.$raise($$$('ArgumentError'), "invalid radix " + (radix));
      }

      if (/^\s*_/.test(string)) {
        return 0;
      }

      string = string.replace(/^(\s*[+-]?)(0[bodx]?)(.+)$/, function (original, head, flag, tail) {
        switch (tail.charAt(0)) {
        case '+':
        case '-':
          return original;
        case '0':
          if (tail.charAt(1) === 'x' && flag === '0x' && (radix === 0 || radix === 16)) {
            return original;
          }
        }
        switch (flag) {
        case '0b':
          if (radix === 0 || radix === 2) {
            radix = 2;
            return head + tail;
          }
          break;
        case '0':
        case '0o':
          if (radix === 0 || radix === 8) {
            radix = 8;
            return head + tail;
          }
          break;
        case '0d':
          if (radix === 0 || radix === 10) {
            radix = 10;
            return head + tail;
          }
          break;
        case '0x':
          if (radix === 0 || radix === 16) {
            radix = 16;
            return head + tail;
          }
          break;
        }
        return original
      });

      result = parseInt(string.replace(/_(?!_)/g, ''), radix);
      return isNaN(result) ? 0 : result;
    }, -1);
    
    $def(self, '$to_proc', function $$to_proc() {
      var self = this, method_name = nil, jsid = nil, proc = nil;

      $$to_proc.$$p = null;
      
      method_name = self.valueOf();
      jsid = Opal.jsid(method_name);
      proc = $send($Kernel, 'proc', [], function $$17($a){var block = $$17.$$p || nil, $post_args, args;

        $$17.$$p = null;
        $post_args = $slice(arguments);
        args = $post_args;
        
        if (args.length === 0) {
          $Kernel.$raise($$$('ArgumentError'), "no receiver given");
        }

        var recv = args[0];

        if (recv == null) recv = nil;

        var body = recv[jsid];

        if (!body) {
          body = recv.$method_missing;
          args[0] = method_name;
        } else {
          args = args.slice(1);
        }

        if (typeof block === 'function') {
          body.$$p = block;
        }

        if (args.length === 0) {
          return body.call(recv);
        } else {
          return body.apply(recv, args);
        }
}, -1);
      proc.$$source_location = nil;
      return proc;
    });
    
    $def(self, '$to_s', function $$to_s() {
      var self = this;

      return self.toString();
    });
    
    $def(self, '$tr', function $$tr(from, to) {
      var self = this;

      
      from = $coerce_to(from, $$$('String'), 'to_str').$to_s();
      to = $coerce_to(to, $$$('String'), 'to_str').$to_s();

      if (from.length == 0 || from === to) {
        return self;
      }

      var i, in_range, c, ch, start, end, length;
      var subs = {};
      var from_chars = from.split('');
      var from_length = from_chars.length;
      var to_chars = to.split('');
      var to_length = to_chars.length;

      var inverse = false;
      var global_sub = null;
      if (from_chars[0] === '^' && from_chars.length > 1) {
        inverse = true;
        from_chars.shift();
        global_sub = to_chars[to_length - 1];
        from_length -= 1;
      }

      var from_chars_expanded = [];
      var last_from = null;
      in_range = false;
      for (i = 0; i < from_length; i++) {
        ch = from_chars[i];
        if (last_from == null) {
          last_from = ch;
          from_chars_expanded.push(ch);
        }
        else if (ch === '-') {
          if (last_from === '-') {
            from_chars_expanded.push('-');
            from_chars_expanded.push('-');
          }
          else if (i == from_length - 1) {
            from_chars_expanded.push('-');
          }
          else {
            in_range = true;
          }
        }
        else if (in_range) {
          start = last_from.charCodeAt(0);
          end = ch.charCodeAt(0);
          if (start > end) {
            $Kernel.$raise($$$('ArgumentError'), "invalid range \"" + (String.fromCharCode(start)) + "-" + (String.fromCharCode(end)) + "\" in string transliteration");
          }
          for (c = start + 1; c < end; c++) {
            from_chars_expanded.push(String.fromCharCode(c));
          }
          from_chars_expanded.push(ch);
          in_range = null;
          last_from = null;
        }
        else {
          from_chars_expanded.push(ch);
        }
      }

      from_chars = from_chars_expanded;
      from_length = from_chars.length;

      if (inverse) {
        for (i = 0; i < from_length; i++) {
          subs[from_chars[i]] = true;
        }
      }
      else {
        if (to_length > 0) {
          var to_chars_expanded = [];
          var last_to = null;
          in_range = false;
          for (i = 0; i < to_length; i++) {
            ch = to_chars[i];
            if (last_to == null) {
              last_to = ch;
              to_chars_expanded.push(ch);
            }
            else if (ch === '-') {
              if (last_to === '-') {
                to_chars_expanded.push('-');
                to_chars_expanded.push('-');
              }
              else if (i == to_length - 1) {
                to_chars_expanded.push('-');
              }
              else {
                in_range = true;
              }
            }
            else if (in_range) {
              start = last_to.charCodeAt(0);
              end = ch.charCodeAt(0);
              if (start > end) {
                $Kernel.$raise($$$('ArgumentError'), "invalid range \"" + (String.fromCharCode(start)) + "-" + (String.fromCharCode(end)) + "\" in string transliteration");
              }
              for (c = start + 1; c < end; c++) {
                to_chars_expanded.push(String.fromCharCode(c));
              }
              to_chars_expanded.push(ch);
              in_range = null;
              last_to = null;
            }
            else {
              to_chars_expanded.push(ch);
            }
          }

          to_chars = to_chars_expanded;
          to_length = to_chars.length;
        }

        var length_diff = from_length - to_length;
        if (length_diff > 0) {
          var pad_char = (to_length > 0 ? to_chars[to_length - 1] : '');
          for (i = 0; i < length_diff; i++) {
            to_chars.push(pad_char);
          }
        }

        for (i = 0; i < from_length; i++) {
          subs[from_chars[i]] = to_chars[i];
        }
      }

      var new_str = '';
      for (i = 0, length = self.length; i < length; i++) {
        ch = self.charAt(i);
        var sub = subs[ch];
        if (inverse) {
          new_str += (sub == null ? global_sub : ch);
        }
        else {
          new_str += (sub != null ? sub : ch);
        }
      }
      return new_str;
    
    });
    
    $def(self, '$tr_s', function $$tr_s(from, to) {
      var self = this;

      
      from = $coerce_to(from, $$$('String'), 'to_str').$to_s();
      to = $coerce_to(to, $$$('String'), 'to_str').$to_s();

      if (from.length == 0) {
        return self;
      }

      var i, in_range, c, ch, start, end, length;
      var subs = {};
      var from_chars = from.split('');
      var from_length = from_chars.length;
      var to_chars = to.split('');
      var to_length = to_chars.length;

      var inverse = false;
      var global_sub = null;
      if (from_chars[0] === '^' && from_chars.length > 1) {
        inverse = true;
        from_chars.shift();
        global_sub = to_chars[to_length - 1];
        from_length -= 1;
      }

      var from_chars_expanded = [];
      var last_from = null;
      in_range = false;
      for (i = 0; i < from_length; i++) {
        ch = from_chars[i];
        if (last_from == null) {
          last_from = ch;
          from_chars_expanded.push(ch);
        }
        else if (ch === '-') {
          if (last_from === '-') {
            from_chars_expanded.push('-');
            from_chars_expanded.push('-');
          }
          else if (i == from_length - 1) {
            from_chars_expanded.push('-');
          }
          else {
            in_range = true;
          }
        }
        else if (in_range) {
          start = last_from.charCodeAt(0);
          end = ch.charCodeAt(0);
          if (start > end) {
            $Kernel.$raise($$$('ArgumentError'), "invalid range \"" + (String.fromCharCode(start)) + "-" + (String.fromCharCode(end)) + "\" in string transliteration");
          }
          for (c = start + 1; c < end; c++) {
            from_chars_expanded.push(String.fromCharCode(c));
          }
          from_chars_expanded.push(ch);
          in_range = null;
          last_from = null;
        }
        else {
          from_chars_expanded.push(ch);
        }
      }

      from_chars = from_chars_expanded;
      from_length = from_chars.length;

      if (inverse) {
        for (i = 0; i < from_length; i++) {
          subs[from_chars[i]] = true;
        }
      }
      else {
        if (to_length > 0) {
          var to_chars_expanded = [];
          var last_to = null;
          in_range = false;
          for (i = 0; i < to_length; i++) {
            ch = to_chars[i];
            if (last_from == null) {
              last_from = ch;
              to_chars_expanded.push(ch);
            }
            else if (ch === '-') {
              if (last_to === '-') {
                to_chars_expanded.push('-');
                to_chars_expanded.push('-');
              }
              else if (i == to_length - 1) {
                to_chars_expanded.push('-');
              }
              else {
                in_range = true;
              }
            }
            else if (in_range) {
              start = last_from.charCodeAt(0);
              end = ch.charCodeAt(0);
              if (start > end) {
                $Kernel.$raise($$$('ArgumentError'), "invalid range \"" + (String.fromCharCode(start)) + "-" + (String.fromCharCode(end)) + "\" in string transliteration");
              }
              for (c = start + 1; c < end; c++) {
                to_chars_expanded.push(String.fromCharCode(c));
              }
              to_chars_expanded.push(ch);
              in_range = null;
              last_from = null;
            }
            else {
              to_chars_expanded.push(ch);
            }
          }

          to_chars = to_chars_expanded;
          to_length = to_chars.length;
        }

        var length_diff = from_length - to_length;
        if (length_diff > 0) {
          var pad_char = (to_length > 0 ? to_chars[to_length - 1] : '');
          for (i = 0; i < length_diff; i++) {
            to_chars.push(pad_char);
          }
        }

        for (i = 0; i < from_length; i++) {
          subs[from_chars[i]] = to_chars[i];
        }
      }
      var new_str = '';
      var last_substitute = null;
      for (i = 0, length = self.length; i < length; i++) {
        ch = self.charAt(i);
        var sub = subs[ch];
        if (inverse) {
          if (sub == null) {
            if (last_substitute == null) {
              new_str += global_sub;
              last_substitute = true;
            }
          }
          else {
            new_str += ch;
            last_substitute = null;
          }
        }
        else {
          if (sub != null) {
            if (last_substitute == null || last_substitute !== sub) {
              new_str += sub;
              last_substitute = sub;
            }
          }
          else {
            new_str += ch;
            last_substitute = null;
          }
        }
      }
      return new_str;
    
    });
    
    $def(self, '$upcase', function $$upcase() {
      var self = this;

      return self.toUpperCase();
    });
    
    $def(self, '$upto', function $$upto(stop, excl) {
      var block = $$upto.$$p || nil, self = this;

      $$upto.$$p = null;
      if (excl == null) excl = false;
      if (!(block !== nil)) {
        return self.$enum_for("upto", stop, excl)
      }      
      var a, b, s = self.toString();

      stop = $coerce_to(stop, $$$('String'), 'to_str');

      if (s.length === 1 && stop.length === 1) {

        a = s.charCodeAt(0);
        b = stop.charCodeAt(0);

        while (a <= b) {
          if (excl && a === b) {
            break;
          }

          block(String.fromCharCode(a));

          a += 1;
        }

      } else if (parseInt(s, 10).toString() === s && parseInt(stop, 10).toString() === stop) {

        a = parseInt(s, 10);
        b = parseInt(stop, 10);

        while (a <= b) {
          if (excl && a === b) {
            break;
          }

          block(a.toString());

          a += 1;
        }

      } else {

        while (s.length <= stop.length && s <= stop) {
          if (excl && s === stop) {
            break;
          }

          block(s);

          s = (s).$succ();
        }

      }
      return self;
    }, -2);
    
    function char_class_from_char_sets(sets) {
      function explode_sequences_in_character_set(set) {
        var result = '',
            i, len = set.length,
            curr_char,
            skip_next_dash,
            char_code_from,
            char_code_upto,
            char_code;
        for (i = 0; i < len; i++) {
          curr_char = set.charAt(i);
          if (curr_char === '-' && i > 0 && i < (len - 1) && !skip_next_dash) {
            char_code_from = set.charCodeAt(i - 1);
            char_code_upto = set.charCodeAt(i + 1);
            if (char_code_from > char_code_upto) {
              $Kernel.$raise($$$('ArgumentError'), "invalid range \"" + (char_code_from) + "-" + (char_code_upto) + "\" in string transliteration");
            }
            for (char_code = char_code_from + 1; char_code < char_code_upto + 1; char_code++) {
              result += String.fromCharCode(char_code);
            }
            skip_next_dash = true;
            i++;
          } else {
            skip_next_dash = (curr_char === '\\');
            result += curr_char;
          }
        }
        return result;
      }

      function intersection(setA, setB) {
        if (setA.length === 0) {
          return setB;
        }
        var result = '',
            i, len = setA.length,
            chr;
        for (i = 0; i < len; i++) {
          chr = setA.charAt(i);
          if (setB.indexOf(chr) !== -1) {
            result += chr;
          }
        }
        return result;
      }

      var i, len, set, neg, chr, tmp,
          pos_intersection = '',
          neg_intersection = '';

      for (i = 0, len = sets.length; i < len; i++) {
        set = $coerce_to(sets[i], $$$('String'), 'to_str');
        neg = (set.charAt(0) === '^' && set.length > 1);
        set = explode_sequences_in_character_set(neg ? set.slice(1) : set);
        if (neg) {
          neg_intersection = intersection(neg_intersection, set);
        } else {
          pos_intersection = intersection(pos_intersection, set);
        }
      }

      if (pos_intersection.length > 0 && neg_intersection.length > 0) {
        tmp = '';
        for (i = 0, len = pos_intersection.length; i < len; i++) {
          chr = pos_intersection.charAt(i);
          if (neg_intersection.indexOf(chr) === -1) {
            tmp += chr;
          }
        }
        pos_intersection = tmp;
        neg_intersection = '';
      }

      if (pos_intersection.length > 0) {
        return '[' + $$$('Regexp').$escape(pos_intersection) + ']';
      }

      if (neg_intersection.length > 0) {
        return '[^' + $$$('Regexp').$escape(neg_intersection) + ']';
      }

      return null;
    }
    
    $def(self, '$instance_variables', function $$instance_variables() {
      
      return []
    });
    $defs(self, '$_load', function $$_load($a) {
      var $post_args, args, self = this;

      
      $post_args = $slice(arguments);
      args = $post_args;
      return $send(self, 'new', $to_a(args));
    }, -1);
    
    $def(self, '$unicode_normalize', function $$unicode_normalize(form) {
      var self = this;

      
      if (form == null) form = "nfc";
      if (!$truthy(["nfc", "nfd", "nfkc", "nfkd"]['$include?'](form))) {
        $Kernel.$raise($$$('ArgumentError'), "Invalid normalization form " + (form));
      }      return self.normalize(form.$upcase());
    }, -1);
    
    $def(self, '$unicode_normalized?', function $String_unicode_normalized$ques$18(form) {
      var self = this;

      
      if (form == null) form = "nfc";
      return self.$unicode_normalize(form)['$=='](self);
    }, -1);
    
    $def(self, '$unpack', function $$unpack(format) {
      
      return $Kernel.$raise("To use String#unpack, you must first require 'corelib/string/unpack'.")
    });
    
    $def(self, '$unpack1', function $$unpack1(format) {
      
      return $Kernel.$raise("To use String#unpack1, you must first require 'corelib/string/unpack'.")
    });
    
    $def(self, '$freeze', function $$freeze() {
      var self = this;

      
      if (typeof self === 'string') { return self; }
      $prop(self, "$$frozen", true);
      return self;
    
    });
    
    $def(self, '$-@', function $String_$minus$$19() {
      var self = this;

      
      if (typeof self === 'string') return self;
      if (self.$$frozen) return self;
      if (self.encoding.name == 'UTF-8' && self.internal_encoding.name == 'UTF-8') return self.toString();
      return self.$dup().$freeze();
    
    });
    
    $def(self, '$frozen?', function $String_frozen$ques$20() {
      var self = this;

      return typeof self === 'string' || self.$$frozen === true;
    });
    $alias(self, "+@", "dup");
    $alias(self, "===", "==");
    $alias(self, "byteslice", "[]");
    $alias(self, "eql?", "==");
    $alias(self, "equal?", "===");
    $alias(self, "object_id", "__id__");
    $alias(self, "slice", "[]");
    $alias(self, "succ", "next");
    $alias(self, "to_str", "to_s");
    $alias(self, "to_sym", "intern");
    return $Opal.$pristine(self, "initialize");
  })('::', String, $nesting);
  return $const_set($nesting[0], 'Symbol', $$('String'));
};

Opal.modules["corelib/enumerable"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $truthy = Opal.truthy, $coerce_to = Opal.coerce_to, $yield1 = Opal.yield1, $yieldX = Opal.yieldX, $deny_frozen_access = Opal.deny_frozen_access, $module = Opal.module, $send = Opal.send, $slice = Opal.slice, $to_a = Opal.to_a, $Opal = Opal.Opal, $thrower = Opal.thrower, $def = Opal.def, $Kernel = Opal.Kernel, $return_val = Opal.return_val, $rb_gt = Opal.rb_gt, $rb_times = Opal.rb_times, $rb_lt = Opal.rb_lt, $eqeq = Opal.eqeq, $rb_plus = Opal.rb_plus, $rb_minus = Opal.rb_minus, $rb_divide = Opal.rb_divide, $rb_le = Opal.rb_le, $hash2 = Opal.hash2, $lambda = Opal.lambda, $not = Opal.not, $alias = Opal.alias, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('each,public_send,destructure,to_enum,enumerator_size,new,yield,raise,slice_when,!,enum_for,flatten,map,to_proc,compact,to_a,warn,proc,==,nil?,respond_to?,coerce_to!,>,*,try_convert,<,+,-,ceil,/,size,select,__send__,length,<=,[],push,<<,[]=,===,inspect,<=>,first,reverse,sort,take,sort_by,compare,call,dup,sort!,map!,include?,-@,key?,values,transform_values,group_by,fetch,to_h,coerce_to?,class,zip,detect,find_all,collect_concat,collect,inject,entries');
  return (function($base, $parent_nesting) {
    var self = $module($base, 'Enumerable');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting);

    
    
    function comparableForPattern(value) {
      if (value.length === 0) {
        value = [nil];
      }

      if (value.length > 1) {
        value = [value];
      }

      return value;
    }
    
    $def(self, '$all?', function $Enumerable_all$ques$1(pattern) {try { var $t_return = $thrower('return'); 
      var block = $Enumerable_all$ques$1.$$p || nil, self = this;

      $Enumerable_all$ques$1.$$p = null;
      
      ;
      ;
      if ($truthy(pattern !== undefined)) {
        $send(self, 'each', [], function $$2($a){var $post_args, value, comparable = nil;

          
          $post_args = $slice(arguments);
          value = $post_args;
          comparable = comparableForPattern(value);
          if ($truthy($send(pattern, 'public_send', ["==="].concat($to_a(comparable))))) {
            return nil
          } else {
            $t_return.$throw(false);
          };}, {$$arity: -1, $$ret: $t_return});
      } else if ((block !== nil)) {
        $send(self, 'each', [], function $$3($a){var $post_args, value;

          
          $post_args = $slice(arguments);
          value = $post_args;
          if ($truthy(Opal.yieldX(block, $to_a(value)))) {
            return nil
          } else {
            $t_return.$throw(false);
          };}, {$$arity: -1, $$ret: $t_return});
      } else {
        $send(self, 'each', [], function $$4($a){var $post_args, value;

          
          $post_args = $slice(arguments);
          value = $post_args;
          if ($truthy($Opal.$destructure(value))) {
            return nil
          } else {
            $t_return.$throw(false);
          };}, {$$arity: -1, $$ret: $t_return});
      };
      return true;} catch($e) {
        if ($e === $t_return) return $e.$v;
        throw $e;
      }
    }, -1);
    
    $def(self, '$any?', function $Enumerable_any$ques$5(pattern) {try { var $t_return = $thrower('return'); 
      var block = $Enumerable_any$ques$5.$$p || nil, self = this;

      $Enumerable_any$ques$5.$$p = null;
      
      ;
      ;
      if ($truthy(pattern !== undefined)) {
        $send(self, 'each', [], function $$6($a){var $post_args, value, comparable = nil;

          
          $post_args = $slice(arguments);
          value = $post_args;
          comparable = comparableForPattern(value);
          if ($truthy($send(pattern, 'public_send', ["==="].concat($to_a(comparable))))) {
            $t_return.$throw(true);
          } else {
            return nil
          };}, {$$arity: -1, $$ret: $t_return});
      } else if ((block !== nil)) {
        $send(self, 'each', [], function $$7($a){var $post_args, value;

          
          $post_args = $slice(arguments);
          value = $post_args;
          if ($truthy(Opal.yieldX(block, $to_a(value)))) {
            $t_return.$throw(true);
          } else {
            return nil
          };}, {$$arity: -1, $$ret: $t_return});
      } else {
        $send(self, 'each', [], function $$8($a){var $post_args, value;

          
          $post_args = $slice(arguments);
          value = $post_args;
          if ($truthy($Opal.$destructure(value))) {
            $t_return.$throw(true);
          } else {
            return nil
          };}, {$$arity: -1, $$ret: $t_return});
      };
      return false;} catch($e) {
        if ($e === $t_return) return $e.$v;
        throw $e;
      }
    }, -1);
    
    $def(self, '$chunk', function $$chunk() {
      var block = $$chunk.$$p || nil, self = this;

      $$chunk.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'to_enum', ["chunk"], function $$9(){var self = $$9.$$s == null ? this : $$9.$$s;

          return self.$enumerator_size()}, {$$s: self})
      }      return $send($$$('Enumerator'), 'new', [], function $$10(yielder){var self = $$10.$$s == null ? this : $$10.$$s;

        
        if (yielder == null) yielder = nil;
        
        var previous = nil, accumulate = [];

        function releaseAccumulate() {
          if (accumulate.length > 0) {
            yielder.$yield(previous, accumulate);
          }
        }

        self.$each.$$p = function(value) {
          var key = $yield1(block, value);

          if (key === nil) {
            releaseAccumulate();
            accumulate = [];
            previous = nil;
          } else {
            if (previous === nil || previous === key) {
              accumulate.push(value);
            } else {
              releaseAccumulate();
              accumulate = [value];
            }

            previous = key;
          }
        };

        self.$each();

        releaseAccumulate();
}, {$$s: self});
    });
    
    $def(self, '$chunk_while', function $$chunk_while() {
      var block = $$chunk_while.$$p || nil, self = this;

      $$chunk_while.$$p = null;
      if (!(block !== nil)) {
        $Kernel.$raise($$$('ArgumentError'), "no block given");
      }      return $send(self, 'slice_when', [], function $$11(before, after){
        
        if (before == null) before = nil;
        if (after == null) after = nil;
        return Opal.yieldX(block, [before, after])['$!']();});
    });
    
    $def(self, '$collect', function $$collect() {
      var block = $$collect.$$p || nil, self = this;

      $$collect.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["collect"], function $$12(){var self = $$12.$$s == null ? this : $$12.$$s;

          return self.$enumerator_size()}, {$$s: self})
      }      
      var result = [];

      self.$each.$$p = function() {
        var value = $yieldX(block, arguments);

        result.push(value);
      };

      self.$each();

      return result;
    });
    
    $def(self, '$collect_concat', function $$collect_concat() {
      var block = $$collect_concat.$$p || nil, self = this;

      $$collect_concat.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["collect_concat"], function $$13(){var self = $$13.$$s == null ? this : $$13.$$s;

          return self.$enumerator_size()}, {$$s: self})
      }      return $send(self, 'map', [], block.$to_proc()).$flatten(1);
    });
    
    $def(self, '$compact', function $$compact() {
      var self = this;

      return self.$to_a().$compact()
    });
    
    $def(self, '$count', function $$count(object) {
      var block = $$count.$$p || nil, self = this, result = nil;

      $$count.$$p = null;
      result = 0;
      
      if (object != null && block !== nil) {
        self.$warn("warning: given block not used");
      }
      if ($truthy(object != null)) {
        block = $send($Kernel, 'proc', [], function $$14($a){var $post_args, args;

          
          $post_args = $slice(arguments);
          args = $post_args;
          return $Opal.$destructure(args)['$=='](object);}, -1);
      } else if ($truthy(block['$nil?']())) {
        block = $send($Kernel, 'proc', [], $return_val(true));
      }      $send(self, 'each', [], function $$15($a){var $post_args, args;

        
        $post_args = $slice(arguments);
        args = $post_args;
        if ($truthy($yieldX(block, args))) {
          return result++;
        } else {
          return nil
        }}, -1);
      return result;
    }, -1);
    
    $def(self, '$cycle', function $$cycle(n) {
      var block = $$cycle.$$p || nil, self = this;

      $$cycle.$$p = null;
      if (n == null) n = nil;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["cycle", n], function $$16(){var self = $$16.$$s == null ? this : $$16.$$s;

          if ($truthy(n['$nil?']())) {
            if ($truthy(self['$respond_to?']("size"))) {
              return $$$($$$('Float'), 'INFINITY')
            } else {
              return nil
            }
          } else {
            
            n = $Opal['$coerce_to!'](n, $$$('Integer'), "to_int");
            if ($truthy($rb_gt(n, 0))) {
              return $rb_times(self.$enumerator_size(), n)
            } else {
              return 0
            }          }}, {$$s: self})
      }      if (!$truthy(n['$nil?']())) {
        
        n = $Opal['$coerce_to!'](n, $$$('Integer'), "to_int");
        if ($truthy(n <= 0)) {
          return nil
        }      }      
      var all = [], i, length;

      self.$each.$$p = function() {
        var param = $Opal.$destructure(arguments);
            $yield1(block, param);

        all.push(param);
      };

      self.$each();

      if (all.length === 0) {
        return nil;
      }

      if (n === nil) {
        while (true) {
          for (i = 0, length = all.length; i < length; i++) {
            $yield1(block, all[i]);
          }
        }
      }
      else {
        while (n > 1) {
          for (i = 0, length = all.length; i < length; i++) {
            $yield1(block, all[i]);
          }

          n--;
        }
      }
    }, -1);
    
    $def(self, '$detect', function $$detect(ifnone) {try { var $t_return = $thrower('return'); 
      var block = $$detect.$$p || nil, self = this;

      $$detect.$$p = null;
      
      ;
      ;
      if (!(block !== nil)) {
        return self.$enum_for("detect", ifnone)
      };
      $send(self, 'each', [], function $$17($a){var $post_args, args, value = nil;

        
        $post_args = $slice(arguments);
        args = $post_args;
        value = $Opal.$destructure(args);
        if ($truthy(Opal.yield1(block, value))) {
          $t_return.$throw(value);
        } else {
          return nil
        };}, {$$arity: -1, $$ret: $t_return});
      
      if (ifnone !== undefined) {
        if (typeof(ifnone) === 'function') {
          return ifnone();
        } else {
          return ifnone;
        }
      }
    ;
      return nil;} catch($e) {
        if ($e === $t_return) return $e.$v;
        throw $e;
      }
    }, -1);
    
    $def(self, '$drop', function $$drop(number) {
      var self = this;

      
      number = $coerce_to(number, $$$('Integer'), 'to_int');
      if ($truthy(number < 0)) {
        $Kernel.$raise($$$('ArgumentError'), "attempt to drop negative size");
      }      
      var result  = [],
          current = 0;

      self.$each.$$p = function() {
        if (number <= current) {
          result.push($Opal.$destructure(arguments));
        }

        current++;
      };

      self.$each();

      return result;
    });
    
    $def(self, '$drop_while', function $$drop_while() {
      var block = $$drop_while.$$p || nil, self = this;

      $$drop_while.$$p = null;
      if (!(block !== nil)) {
        return self.$enum_for("drop_while")
      }      
      var result   = [],
          dropping = true;

      self.$each.$$p = function() {
        var param = $Opal.$destructure(arguments);

        if (dropping) {
          var value = $yield1(block, param);

          if (!$truthy(value)) {
            dropping = false;
            result.push(param);
          }
        }
        else {
          result.push(param);
        }
      };

      self.$each();

      return result;
    });
    
    $def(self, '$each_cons', function $$each_cons(n) {
      var block = $$each_cons.$$p || nil, self = this;

      $$each_cons.$$p = null;
      if ($truthy(arguments.length != 1)) {
        $Kernel.$raise($$$('ArgumentError'), "wrong number of arguments (" + (arguments.length) + " for 1)");
      }      n = $Opal.$try_convert(n, $$$('Integer'), "to_int");
      if ($truthy(n <= 0)) {
        $Kernel.$raise($$$('ArgumentError'), "invalid size");
      }      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["each_cons", n], function $$18(){var self = $$18.$$s == null ? this : $$18.$$s, enum_size = nil;

          
          enum_size = self.$enumerator_size();
          if ($truthy(enum_size['$nil?']())) {
            return nil
          } else if (($eqeq(enum_size, 0) || ($truthy($rb_lt(enum_size, n))))) {
            return 0
          } else {
            return $rb_plus($rb_minus(enum_size, n), 1)
          }}, {$$s: self})
      }      
      var buffer = [];

      self.$each.$$p = function() {
        var element = $Opal.$destructure(arguments);
        buffer.push(element);
        if (buffer.length > n) {
          buffer.shift();
        }
        if (buffer.length == n) {
          $yield1(block, buffer.slice(0, n));
        }
      };

      self.$each();

      return self;
    });
    
    $def(self, '$each_entry', function $$each_entry($a) {
      var block = $$each_entry.$$p || nil, $post_args, data, self = this;

      $$each_entry.$$p = null;
      $post_args = $slice(arguments);
      data = $post_args;
      if (!(block !== nil)) {
        return $send(self, 'to_enum', ["each_entry"].concat($to_a(data)), function $$19(){var self = $$19.$$s == null ? this : $$19.$$s;

          return self.$enumerator_size()}, {$$s: self})
      }      
      self.$each.$$p = function() {
        var item = $Opal.$destructure(arguments);

        $yield1(block, item);
      };

      self.$each.apply(self, data);

      return self;
    }, -1);
    
    $def(self, '$each_slice', function $$each_slice(n) {
      var block = $$each_slice.$$p || nil, self = this;

      $$each_slice.$$p = null;
      n = $coerce_to(n, $$$('Integer'), 'to_int');
      if ($truthy(n <= 0)) {
        $Kernel.$raise($$$('ArgumentError'), "invalid slice size");
      }      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["each_slice", n], function $$20(){var self = $$20.$$s == null ? this : $$20.$$s;

          if ($truthy(self['$respond_to?']("size"))) {
            return $rb_divide(self.$size(), n).$ceil()
          } else {
            return nil
          }}, {$$s: self})
      }      
      var slice = [];

      self.$each.$$p = function() {
        var param = $Opal.$destructure(arguments);

        slice.push(param);

        if (slice.length === n) {
          $yield1(block, slice);
          slice = [];
        }
      };

      self.$each();

      // our "last" group, if smaller than n then won't have been yielded
      if (slice.length > 0) {
        $yield1(block, slice);
      }
      return self;
    });
    
    $def(self, '$each_with_index', function $$each_with_index($a) {
      var block = $$each_with_index.$$p || nil, $post_args, args, self = this;

      $$each_with_index.$$p = null;
      $post_args = $slice(arguments);
      args = $post_args;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["each_with_index"].concat($to_a(args)), function $$21(){var self = $$21.$$s == null ? this : $$21.$$s;

          return self.$enumerator_size()}, {$$s: self})
      }      
      var index = 0;

      self.$each.$$p = function() {
        var param = $Opal.$destructure(arguments);

        block(param, index);

        index++;
      };

      self.$each.apply(self, args);
      return self;
    }, -1);
    
    $def(self, '$each_with_object', function $$each_with_object(object) {
      var block = $$each_with_object.$$p || nil, self = this;

      $$each_with_object.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["each_with_object", object], function $$22(){var self = $$22.$$s == null ? this : $$22.$$s;

          return self.$enumerator_size()}, {$$s: self})
      }      
      self.$each.$$p = function() {
        var param = $Opal.$destructure(arguments);

        block(param, object);
      };

      self.$each();
      return object;
    });
    
    $def(self, '$entries', function $$entries($a) {
      var $post_args, args, self = this;

      
      $post_args = $slice(arguments);
      args = $post_args;
      
      var result = [];

      self.$each.$$p = function() {
        result.push($Opal.$destructure(arguments));
      };

      self.$each.apply(self, args);

      return result;
    }, -1);
    
    $def(self, '$filter_map', function $$filter_map() {
      var block = $$filter_map.$$p || nil, self = this;

      $$filter_map.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["filter_map"], function $$23(){var self = $$23.$$s == null ? this : $$23.$$s;

          return self.$enumerator_size()}, {$$s: self})
      }      return $send($send(self, 'map', [], block.$to_proc()), 'select', [], "itself".$to_proc());
    });
    
    $def(self, '$find_all', function $$find_all() {
      var block = $$find_all.$$p || nil, self = this;

      $$find_all.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["find_all"], function $$24(){var self = $$24.$$s == null ? this : $$24.$$s;

          return self.$enumerator_size()}, {$$s: self})
      }      
      var result = [];

      self.$each.$$p = function() {
        var param = $Opal.$destructure(arguments),
            value = $yield1(block, param);

        if ($truthy(value)) {
          result.push(param);
        }
      };

      self.$each();

      return result;
    });
    
    $def(self, '$find_index', function $$find_index(object) {try { var $t_return = $thrower('return'); 
      var block = $$find_index.$$p || nil, self = this, index = nil;

      $$find_index.$$p = null;
      
      ;
      ;
      if ($truthy(object === undefined && block === nil)) {
        return self.$enum_for("find_index")
      };
      
      if (object != null && block !== nil) {
        self.$warn("warning: given block not used");
      }
    ;
      index = 0;
      if ($truthy(object != null)) {
        $send(self, 'each', [], function $$25($a){var $post_args, value;

          
          $post_args = $slice(arguments);
          value = $post_args;
          if ($eqeq($Opal.$destructure(value), object)) {
            $t_return.$throw(index);
          };
          return index += 1;;}, {$$arity: -1, $$ret: $t_return});
      } else {
        $send(self, 'each', [], function $$26($a){var $post_args, value;

          
          $post_args = $slice(arguments);
          value = $post_args;
          if ($truthy(Opal.yieldX(block, $to_a(value)))) {
            $t_return.$throw(index);
          };
          return index += 1;;}, {$$arity: -1, $$ret: $t_return});
      };
      return nil;} catch($e) {
        if ($e === $t_return) return $e.$v;
        throw $e;
      }
    }, -1);
    
    $def(self, '$first', function $$first(number) {try { var $t_return = $thrower('return'); 
      var self = this, result = nil, current = nil;

      
      ;
      if ($truthy(number === undefined)) {
        return $send(self, 'each', [], function $$27(value){
          
          if (value == null) value = nil;
          $t_return.$throw(value);}, {$$ret: $t_return})
      } else {
        
        result = [];
        number = $coerce_to(number, $$$('Integer'), 'to_int');
        if ($truthy(number < 0)) {
          $Kernel.$raise($$$('ArgumentError'), "attempt to take negative size");
        };
        if ($truthy(number == 0)) {
          return []
        };
        current = 0;
        $send(self, 'each', [], function $$28($a){var $post_args, args;

          
          $post_args = $slice(arguments);
          args = $post_args;
          result.push($Opal.$destructure(args));
          if ($truthy(number <= ++current)) {
            $t_return.$throw(result);
          } else {
            return nil
          };}, {$$arity: -1, $$ret: $t_return});
        return result;
      };} catch($e) {
        if ($e === $t_return) return $e.$v;
        throw $e;
      }
    }, -1);
    
    $def(self, '$grep', function $$grep(pattern) {
      var block = $$grep.$$p || nil, self = this, result = nil;

      $$grep.$$p = null;
      result = [];
      $send(self, 'each', [], function $$29($a){var $post_args, value, cmp = nil;

        
        $post_args = $slice(arguments);
        value = $post_args;
        cmp = comparableForPattern(value);
        if (!$truthy($send(pattern, '__send__', ["==="].concat($to_a(cmp))))) {
          return nil
        }        if ((block !== nil)) {
          
          if ($truthy($rb_gt(value.$length(), 1))) {
            value = [value];
          }          value = Opal.yieldX(block, $to_a(value));
        } else if ($truthy($rb_le(value.$length(), 1))) {
          value = value['$[]'](0);
        }        return result.$push(value);}, -1);
      return result;
    });
    
    $def(self, '$grep_v', function $$grep_v(pattern) {
      var block = $$grep_v.$$p || nil, self = this, result = nil;

      $$grep_v.$$p = null;
      result = [];
      $send(self, 'each', [], function $$30($a){var $post_args, value, cmp = nil;

        
        $post_args = $slice(arguments);
        value = $post_args;
        cmp = comparableForPattern(value);
        if ($truthy($send(pattern, '__send__', ["==="].concat($to_a(cmp))))) {
          return nil
        }        if ((block !== nil)) {
          
          if ($truthy($rb_gt(value.$length(), 1))) {
            value = [value];
          }          value = Opal.yieldX(block, $to_a(value));
        } else if ($truthy($rb_le(value.$length(), 1))) {
          value = value['$[]'](0);
        }        return result.$push(value);}, -1);
      return result;
    });
    
    $def(self, '$group_by', function $$group_by() {
      var block = $$group_by.$$p || nil, $a, self = this, hash = nil, $ret_or_1 = nil;

      $$group_by.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["group_by"], function $$31(){var self = $$31.$$s == null ? this : $$31.$$s;

          return self.$enumerator_size()}, {$$s: self})
      }      hash = $hash2([], {});

      self.$each.$$p = function() {
        var param = $Opal.$destructure(arguments),
            value = $yield1(block, param);

        ($truthy(($ret_or_1 = hash['$[]'](value))) ? ($ret_or_1) : (($a = [value, []], $send(hash, '[]=', $a), $a[$a.length - 1])))['$<<'](param);
      };

      self.$each();
      return hash;
    });
    
    $def(self, '$include?', function $Enumerable_include$ques$32(obj) {try { var $t_return = $thrower('return'); 
      var self = this;

      
      $send(self, 'each', [], function $$33($a){var $post_args, args;

        
        $post_args = $slice(arguments);
        args = $post_args;
        if ($eqeq($Opal.$destructure(args), obj)) {
          $t_return.$throw(true);
        } else {
          return nil
        };}, {$$arity: -1, $$ret: $t_return});
      return false;} catch($e) {
        if ($e === $t_return) return $e.$v;
        throw $e;
      }
    });
    
    $def(self, '$inject', function $$inject(object, sym) {
      var block = $$inject.$$p || nil, self = this;

      $$inject.$$p = null;
      
      var result = object;

      if (block !== nil && sym === undefined) {
        self.$each.$$p = function() {
          var value = $Opal.$destructure(arguments);

          if (result === undefined) {
            result = value;
            return;
          }

          value = $yieldX(block, [result, value]);

          result = value;
        };
      }
      else {
        if (sym === undefined) {
          if (!$$$('Symbol')['$==='](object)) {
            $Kernel.$raise($$$('TypeError'), "" + (object.$inspect()) + " is not a Symbol");
          }

          sym    = object;
          result = undefined;
        }

        self.$each.$$p = function() {
          var value = $Opal.$destructure(arguments);

          if (result === undefined) {
            result = value;
            return;
          }

          result = (result).$__send__(sym, value);
        };
      }

      self.$each();

      return result == undefined ? nil : result;
    }, -1);
    
    $def(self, '$lazy', function $$lazy() {
      var self = this;

      return $send($$$($$$('Enumerator'), 'Lazy'), 'new', [self, self.$enumerator_size()], function $$34(enum$, $a){var $post_args, args;

        
        if (enum$ == null) enum$ = nil;
        $post_args = $slice(arguments, 1);
        args = $post_args;
        return $send(enum$, 'yield', $to_a(args));}, -2)
    });
    
    $def(self, '$enumerator_size', function $$enumerator_size() {
      var self = this;

      if ($truthy(self['$respond_to?']("size"))) {
        return self.$size()
      } else {
        return nil
      }
    });
    
    $def(self, '$max', function $$max(n) {
      var block = $$max.$$p || nil, self = this;

      $$max.$$p = null;
      
      if (n === undefined || n === nil) {
        var result, value;

        self.$each.$$p = function() {
          var item = $Opal.$destructure(arguments);

          if (result === undefined) {
            result = item;
            return;
          }

          if (block !== nil) {
            value = $yieldX(block, [item, result]);
          } else {
            value = (item)['$<=>'](result);
          }

          if (value === nil) {
            $Kernel.$raise($$$('ArgumentError'), "comparison failed");
          }

          if (value > 0) {
            result = item;
          }
        };

        self.$each();

        if (result === undefined) {
          return nil;
        } else {
          return result;
        }
      }

      n = $coerce_to(n, $$$('Integer'), 'to_int');
      return $send(self, 'sort', [], block.$to_proc()).$reverse().$first(n);
    }, -1);
    
    $def(self, '$max_by', function $$max_by(n) {
      var block = $$max_by.$$p || nil, self = this;

      $$max_by.$$p = null;
      if (n == null) n = nil;
      if (!$truthy(block)) {
        return $send(self, 'enum_for', ["max_by", n], function $$35(){var self = $$35.$$s == null ? this : $$35.$$s;

          return self.$enumerator_size()}, {$$s: self})
      }      if (!$truthy(n['$nil?']())) {
        return $send(self, 'sort_by', [], block.$to_proc()).$reverse().$take(n)
      }      
      var result,
          by;

      self.$each.$$p = function() {
        var param = $Opal.$destructure(arguments),
            value = $yield1(block, param);

        if (result === undefined) {
          result = param;
          by     = value;
          return;
        }

        if ((value)['$<=>'](by) > 0) {
          result = param;
          by     = value;
        }
      };

      self.$each();

      return result === undefined ? nil : result;
    }, -1);
    
    $def(self, '$min', function $$min(n) {
      var block = $$min.$$p || nil, self = this;

      $$min.$$p = null;
      if (n == null) n = nil;
      if (!$truthy(n['$nil?']())) {
        if ((block !== nil)) {
          return $send(self, 'sort', [], function $$36(a, b){
            
            if (a == null) a = nil;
            if (b == null) b = nil;
            return Opal.yieldX(block, [a, b]);}).$take(n)
        } else {
          return self.$sort().$take(n)
        }
      }      
      var result;

      if (block !== nil) {
        self.$each.$$p = function() {
          var param = $Opal.$destructure(arguments);

          if (result === undefined) {
            result = param;
            return;
          }

          var value = block(param, result);

          if (value === nil) {
            $Kernel.$raise($$$('ArgumentError'), "comparison failed");
          }

          if (value < 0) {
            result = param;
          }
        };
      }
      else {
        self.$each.$$p = function() {
          var param = $Opal.$destructure(arguments);

          if (result === undefined) {
            result = param;
            return;
          }

          if ($Opal.$compare(param, result) < 0) {
            result = param;
          }
        };
      }

      self.$each();

      return result === undefined ? nil : result;
    }, -1);
    
    $def(self, '$min_by', function $$min_by(n) {
      var block = $$min_by.$$p || nil, self = this;

      $$min_by.$$p = null;
      if (n == null) n = nil;
      if (!$truthy(block)) {
        return $send(self, 'enum_for', ["min_by", n], function $$37(){var self = $$37.$$s == null ? this : $$37.$$s;

          return self.$enumerator_size()}, {$$s: self})
      }      if (!$truthy(n['$nil?']())) {
        return $send(self, 'sort_by', [], block.$to_proc()).$take(n)
      }      
      var result,
          by;

      self.$each.$$p = function() {
        var param = $Opal.$destructure(arguments),
            value = $yield1(block, param);

        if (result === undefined) {
          result = param;
          by     = value;
          return;
        }

        if ((value)['$<=>'](by) < 0) {
          result = param;
          by     = value;
        }
      };

      self.$each();

      return result === undefined ? nil : result;
    }, -1);
    
    $def(self, '$minmax', function $$minmax() {
      var block = $$minmax.$$p || nil, self = this, $ret_or_1 = nil;

      $$minmax.$$p = null;
      block = ($truthy(($ret_or_1 = block)) ? ($ret_or_1) : ($send($Kernel, 'proc', [], function $$38(a, b){
        
        if (a == null) a = nil;
        if (b == null) b = nil;
        return a['$<=>'](b);})));
      
      var min = nil, max = nil, first_time = true;

      self.$each.$$p = function() {
        var element = $Opal.$destructure(arguments);
        if (first_time) {
          min = max = element;
          first_time = false;
        } else {
          var min_cmp = block.$call(min, element);

          if (min_cmp === nil) {
            $Kernel.$raise($$$('ArgumentError'), "comparison failed");
          } else if (min_cmp > 0) {
            min = element;
          }

          var max_cmp = block.$call(max, element);

          if (max_cmp === nil) {
            $Kernel.$raise($$$('ArgumentError'), "comparison failed");
          } else if (max_cmp < 0) {
            max = element;
          }
        }
      };

      self.$each();

      return [min, max];
    });
    
    $def(self, '$minmax_by', function $$minmax_by() {
      var block = $$minmax_by.$$p || nil, self = this;

      $$minmax_by.$$p = null;
      if (!$truthy(block)) {
        return $send(self, 'enum_for', ["minmax_by"], function $$39(){var self = $$39.$$s == null ? this : $$39.$$s;

          return self.$enumerator_size()}, {$$s: self})
      }      
      var min_result = nil,
          max_result = nil,
          min_by,
          max_by;

      self.$each.$$p = function() {
        var param = $Opal.$destructure(arguments),
            value = $yield1(block, param);

        if ((min_by === undefined) || (value)['$<=>'](min_by) < 0) {
          min_result = param;
          min_by     = value;
        }

        if ((max_by === undefined) || (value)['$<=>'](max_by) > 0) {
          max_result = param;
          max_by     = value;
        }
      };

      self.$each();

      return [min_result, max_result];
    });
    
    $def(self, '$none?', function $Enumerable_none$ques$40(pattern) {try { var $t_return = $thrower('return'); 
      var block = $Enumerable_none$ques$40.$$p || nil, self = this;

      $Enumerable_none$ques$40.$$p = null;
      
      ;
      ;
      if ($truthy(pattern !== undefined)) {
        $send(self, 'each', [], function $$41($a){var $post_args, value, comparable = nil;

          
          $post_args = $slice(arguments);
          value = $post_args;
          comparable = comparableForPattern(value);
          if ($truthy($send(pattern, 'public_send', ["==="].concat($to_a(comparable))))) {
            $t_return.$throw(false);
          } else {
            return nil
          };}, {$$arity: -1, $$ret: $t_return});
      } else if ((block !== nil)) {
        $send(self, 'each', [], function $$42($a){var $post_args, value;

          
          $post_args = $slice(arguments);
          value = $post_args;
          if ($truthy(Opal.yieldX(block, $to_a(value)))) {
            $t_return.$throw(false);
          } else {
            return nil
          };}, {$$arity: -1, $$ret: $t_return});
      } else {
        $send(self, 'each', [], function $$43($a){var $post_args, value, item = nil;

          
          $post_args = $slice(arguments);
          value = $post_args;
          item = $Opal.$destructure(value);
          if ($truthy(item)) {
            $t_return.$throw(false);
          } else {
            return nil
          };}, {$$arity: -1, $$ret: $t_return});
      };
      return true;} catch($e) {
        if ($e === $t_return) return $e.$v;
        throw $e;
      }
    }, -1);
    
    $def(self, '$one?', function $Enumerable_one$ques$44(pattern) {try { var $t_return = $thrower('return'); 
      var block = $Enumerable_one$ques$44.$$p || nil, self = this, count = nil;

      $Enumerable_one$ques$44.$$p = null;
      
      ;
      ;
      count = 0;
      if ($truthy(pattern !== undefined)) {
        $send(self, 'each', [], function $$45($a){var $post_args, value, comparable = nil;

          
          $post_args = $slice(arguments);
          value = $post_args;
          comparable = comparableForPattern(value);
          if ($truthy($send(pattern, 'public_send', ["==="].concat($to_a(comparable))))) {
            
            count = $rb_plus(count, 1);
            if ($truthy($rb_gt(count, 1))) {
              $t_return.$throw(false);
            } else {
              return nil
            };
          } else {
            return nil
          };}, {$$arity: -1, $$ret: $t_return});
      } else if ((block !== nil)) {
        $send(self, 'each', [], function $$46($a){var $post_args, value;

          
          $post_args = $slice(arguments);
          value = $post_args;
          if (!$truthy(Opal.yieldX(block, $to_a(value)))) {
            return nil
          };
          count = $rb_plus(count, 1);
          if ($truthy($rb_gt(count, 1))) {
            $t_return.$throw(false);
          } else {
            return nil
          };}, {$$arity: -1, $$ret: $t_return});
      } else {
        $send(self, 'each', [], function $$47($a){var $post_args, value;

          
          $post_args = $slice(arguments);
          value = $post_args;
          if (!$truthy($Opal.$destructure(value))) {
            return nil
          };
          count = $rb_plus(count, 1);
          if ($truthy($rb_gt(count, 1))) {
            $t_return.$throw(false);
          } else {
            return nil
          };}, {$$arity: -1, $$ret: $t_return});
      };
      return count['$=='](1);} catch($e) {
        if ($e === $t_return) return $e.$v;
        throw $e;
      }
    }, -1);
    
    $def(self, '$partition', function $$partition() {
      var block = $$partition.$$p || nil, self = this;

      $$partition.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["partition"], function $$48(){var self = $$48.$$s == null ? this : $$48.$$s;

          return self.$enumerator_size()}, {$$s: self})
      }      
      var truthy = [], falsy = [];

      self.$each.$$p = function() {
        var param = $Opal.$destructure(arguments),
            value = $yield1(block, param);

        if ($truthy(value)) {
          truthy.push(param);
        }
        else {
          falsy.push(param);
        }
      };

      self.$each();

      return [truthy, falsy];
    });
    
    $def(self, '$reject', function $$reject() {
      var block = $$reject.$$p || nil, self = this;

      $$reject.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["reject"], function $$49(){var self = $$49.$$s == null ? this : $$49.$$s;

          return self.$enumerator_size()}, {$$s: self})
      }      
      var result = [];

      self.$each.$$p = function() {
        var param = $Opal.$destructure(arguments),
            value = $yield1(block, param);

        if (!$truthy(value)) {
          result.push(param);
        }
      };

      self.$each();

      return result;
    });
    
    $def(self, '$reverse_each', function $$reverse_each() {
      var block = $$reverse_each.$$p || nil, self = this;

      $$reverse_each.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["reverse_each"], function $$50(){var self = $$50.$$s == null ? this : $$50.$$s;

          return self.$enumerator_size()}, {$$s: self})
      }      
      var result = [];

      self.$each.$$p = function() {
        result.push(arguments);
      };

      self.$each();

      for (var i = result.length - 1; i >= 0; i--) {
        $yieldX(block, result[i]);
      }

      return result;
    });
    
    $def(self, '$slice_before', function $$slice_before(pattern) {
      var block = $$slice_before.$$p || nil, self = this;

      $$slice_before.$$p = null;
      if ($truthy(pattern === undefined && block === nil)) {
        $Kernel.$raise($$$('ArgumentError'), "both pattern and block are given");
      }      if ($truthy(pattern !== undefined && block !== nil || arguments.length > 1)) {
        $Kernel.$raise($$$('ArgumentError'), "wrong number of arguments (" + (arguments.length) + " expected 1)");
      }      return $send($$$('Enumerator'), 'new', [], function $$51(e){var self = $$51.$$s == null ? this : $$51.$$s;

        
        if (e == null) e = nil;
        
        var slice = [];

        if (block !== nil) {
          if (pattern === undefined) {
            self.$each.$$p = function() {
              var param = $Opal.$destructure(arguments),
                  value = $yield1(block, param);

              if ($truthy(value) && slice.length > 0) {
                e['$<<'](slice);
                slice = [];
              }

              slice.push(param);
            };
          }
          else {
            self.$each.$$p = function() {
              var param = $Opal.$destructure(arguments),
                  value = block(param, pattern.$dup());

              if ($truthy(value) && slice.length > 0) {
                e['$<<'](slice);
                slice = [];
              }

              slice.push(param);
            };
          }
        }
        else {
          self.$each.$$p = function() {
            var param = $Opal.$destructure(arguments),
                value = pattern['$==='](param);

            if ($truthy(value) && slice.length > 0) {
              e['$<<'](slice);
              slice = [];
            }

            slice.push(param);
          };
        }

        self.$each();

        if (slice.length > 0) {
          e['$<<'](slice);
        }
}, {$$s: self});
    }, -1);
    
    $def(self, '$slice_after', function $$slice_after(pattern) {
      var block = $$slice_after.$$p || nil, self = this;

      $$slice_after.$$p = null;
      if ($truthy(pattern === undefined && block === nil)) {
        $Kernel.$raise($$$('ArgumentError'), "both pattern and block are given");
      }      if ($truthy(pattern !== undefined && block !== nil || arguments.length > 1)) {
        $Kernel.$raise($$$('ArgumentError'), "wrong number of arguments (" + (arguments.length) + " expected 1)");
      }      if ($truthy(pattern !== undefined)) {
        block = $send($Kernel, 'proc', [], function $$52(e){
          
          if (e == null) e = nil;
          return pattern['$==='](e);});
      }      return $send($$$('Enumerator'), 'new', [], function $$53(yielder){var self = $$53.$$s == null ? this : $$53.$$s;

        
        if (yielder == null) yielder = nil;
        
        var accumulate;

        self.$each.$$p = function() {
          var element = $Opal.$destructure(arguments),
              end_chunk = $yield1(block, element);

          if (accumulate == null) {
            accumulate = [];
          }

          if ($truthy(end_chunk)) {
            accumulate.push(element);
            yielder.$yield(accumulate);
            accumulate = null;
          } else {
            accumulate.push(element);
          }
        };

        self.$each();

        if (accumulate != null) {
          yielder.$yield(accumulate);
        }
}, {$$s: self});
    }, -1);
    
    $def(self, '$slice_when', function $$slice_when() {
      var block = $$slice_when.$$p || nil, self = this;

      $$slice_when.$$p = null;
      if (!(block !== nil)) {
        $Kernel.$raise($$$('ArgumentError'), "wrong number of arguments (0 for 1)");
      }      return $send($$$('Enumerator'), 'new', [], function $$54(yielder){var self = $$54.$$s == null ? this : $$54.$$s;

        
        if (yielder == null) yielder = nil;
        
        var slice = nil, last_after = nil;

        self.$each_cons.$$p = function() {
          var params = $Opal.$destructure(arguments),
              before = params[0],
              after = params[1],
              match = $yieldX(block, [before, after]);

          last_after = after;

          if (slice === nil) {
            slice = [];
          }

          if ($truthy(match)) {
            slice.push(before);
            yielder.$yield(slice);
            slice = [];
          } else {
            slice.push(before);
          }
        };

        self.$each_cons(2);

        if (slice !== nil) {
          slice.push(last_after);
          yielder.$yield(slice);
        }
}, {$$s: self});
    });
    
    $def(self, '$sort', function $$sort() {
      var block = $$sort.$$p || nil, self = this, ary = nil;

      $$sort.$$p = null;
      ary = self.$to_a();
      if (!(block !== nil)) {
        block = $lambda(function $$55(a, b){
          
          if (a == null) a = nil;
          if (b == null) b = nil;
          return a['$<=>'](b);});
      }      return $send(ary, 'sort', [], block.$to_proc());
    });
    
    $def(self, '$sort_by', function $$sort_by() {
      var block = $$sort_by.$$p || nil, self = this, dup = nil;

      $$sort_by.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["sort_by"], function $$56(){var self = $$56.$$s == null ? this : $$56.$$s;

          return self.$enumerator_size()}, {$$s: self})
      }      dup = $send(self, 'map', [], function $$57(){var arg = nil;

        
        arg = $Opal.$destructure(arguments);
        return [Opal.yield1(block, arg), arg];});
      $send(dup, 'sort!', [], function $$58(a, b){
        
        if (a == null) a = nil;
        if (b == null) b = nil;
        return (a[0])['$<=>'](b[0]);});
      return $send(dup, 'map!', [], function $$59(i){
        
        if (i == null) i = nil;
        return i[1];});
    });
    
    $def(self, '$sum', function $$sum(initial) {
      var $yield = $$sum.$$p || nil, self = this, result = nil, compensation = nil;

      $$sum.$$p = null;
      
      if (initial == null) initial = 0;
      result = initial;
      compensation = 0;
      $send(self, 'each', [], function $$60($a){var $post_args, args, item = nil, y = nil, t = nil;

        
        $post_args = $slice(arguments);
        args = $post_args;
        item = (($yield !== nil) ? (Opal.yieldX($yield, $to_a(args))) : ($Opal.$destructure(args)));
        if (($not([$$$($$$('Float'), 'INFINITY'), $$$($$$('Float'), 'INFINITY')['$-@']()]['$include?'](item)) && ($truthy(item['$respond_to?']("-"))))) {
          
          y = $rb_minus(item, compensation);
          t = $rb_plus(result, y);
          compensation = $rb_minus($rb_minus(t, result), y);
          return (result = t);
        } else {
          return (result = $rb_plus(result, item))
        }}, -1);
      return result;
    }, -1);
    
    $def(self, '$take', function $$take(num) {
      var self = this;

      return self.$first(num)
    });
    
    $def(self, '$take_while', function $$take_while() {try { var $t_return = $thrower('return'); 
      var block = $$take_while.$$p || nil, self = this, result = nil;

      $$take_while.$$p = null;
      
      ;
      if (!$truthy(block)) {
        return self.$enum_for("take_while")
      };
      result = [];
      return $send(self, 'each', [], function $$61($a){var $post_args, args, value = nil;

        
        $post_args = $slice(arguments);
        args = $post_args;
        value = $Opal.$destructure(args);
        if (!$truthy(Opal.yield1(block, value))) {
          $t_return.$throw(result);
        };
        return result.push(value);;}, {$$arity: -1, $$ret: $t_return});} catch($e) {
        if ($e === $t_return) return $e.$v;
        throw $e;
      }
    });
    
    $def(self, '$uniq', function $$uniq() {
      var block = $$uniq.$$p || nil, self = this, hash = nil;

      $$uniq.$$p = null;
      hash = $hash2([], {});
      $send(self, 'each', [], function $$62($a){var $post_args, args, $b, value = nil, produced = nil;

        
        $post_args = $slice(arguments);
        args = $post_args;
        value = $Opal.$destructure(args);
        produced = ((block !== nil) ? (Opal.yield1(block, value)) : (value));
        if ($truthy(hash['$key?'](produced))) {
          return nil
        } else {
          return ($b = [produced, value], $send(hash, '[]=', $b), $b[$b.length - 1])
        }}, -1);
      return hash.$values();
    });
    
    $def(self, '$tally', function $$tally(hash) {
      var self = this, out = nil;
      if (hash && hash !== nil) { $deny_frozen_access(hash); }      out = $send($send(self, 'group_by', [], "itself".$to_proc()), 'transform_values', [], "count".$to_proc());
      if ($truthy(hash)) {
        
        $send(out, 'each', [], function $$63(k, v){var $a;

          
          if (k == null) k = nil;
          if (v == null) v = nil;
          return ($a = [k, $rb_plus(hash.$fetch(k, 0), v)], $send(hash, '[]=', $a), $a[$a.length - 1]);});
        return hash;
      } else {
        return out
      }    }, -1);
    
    $def(self, '$to_h', function $$to_h($a) {
      var block = $$to_h.$$p || nil, $post_args, args, self = this;

      $$to_h.$$p = null;
      $post_args = $slice(arguments);
      args = $post_args;
      if ((block !== nil)) {
        return $send($send(self, 'map', [], block.$to_proc()), 'to_h', $to_a(args))
      }      
      var hash = $hash2([], {});

      self.$each.$$p = function() {
        var param = $Opal.$destructure(arguments);
        var ary = $Opal['$coerce_to?'](param, $$$('Array'), "to_ary"), key, val;
        if (!ary.$$is_array) {
          $Kernel.$raise($$$('TypeError'), "wrong element type " + ((ary).$class()) + " (expected array)");
        }
        if (ary.length !== 2) {
          $Kernel.$raise($$$('ArgumentError'), "wrong array length (expected 2, was " + ((ary).$length()) + ")");
        }
        key = ary[0];
        val = ary[1];

        Opal.hash_put(hash, key, val);
      };

      self.$each.apply(self, args);

      return hash;
    }, -1);
    
    $def(self, '$to_set', function $$to_set($a, $b) {
      var block = $$to_set.$$p || nil, $post_args, klass, args, self = this;

      $$to_set.$$p = null;
      $post_args = $slice(arguments);
      
      if ($post_args.length > 0) klass = $post_args.shift();if (klass == null) klass = $$('Set');
      args = $post_args;
      return $send(klass, 'new', [self].concat($to_a(args)), block.$to_proc());
    }, -1);
    
    $def(self, '$zip', function $$zip($a) {
      var $post_args, others, self = this;

      $$zip.$$p = null;
      $post_args = $slice(arguments);
      others = $post_args;
      return $send(self.$to_a(), 'zip', $to_a(others));
    }, -1);
    $alias(self, "find", "detect");
    $alias(self, "filter", "find_all");
    $alias(self, "flat_map", "collect_concat");
    $alias(self, "map", "collect");
    $alias(self, "member?", "include?");
    $alias(self, "reduce", "inject");
    $alias(self, "select", "find_all");
    return $alias(self, "to_a", "entries");
  })('::', $nesting)
};

Opal.modules["corelib/enumerator/arithmetic_sequence"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $truthy = Opal.truthy, $to_a = Opal.to_a, $eqeq = Opal.eqeq, $Kernel = Opal.Kernel, $def = Opal.def, $rb_gt = Opal.rb_gt, $rb_lt = Opal.rb_lt, $rb_le = Opal.rb_le, $rb_ge = Opal.rb_ge, $rb_plus = Opal.rb_plus, $rb_minus = Opal.rb_minus, $eqeqeq = Opal.eqeqeq, $not = Opal.not, $rb_times = Opal.rb_times, $rb_divide = Opal.rb_divide, $alias = Opal.alias, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('is_a?,==,raise,respond_to?,class,attr_reader,begin,end,exclude_end?,>,step,<,<=,>=,-@,_lesser_than_end?,<<,+,-,===,%,_greater_than_begin?,reverse,!,include?,*,to_i,abs,/,hash,inspect');
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Enumerator');

    var $nesting = [self].concat($parent_nesting);

    return (function($base, $super, $parent_nesting) {
      var self = $klass($base, $super, 'ArithmeticSequence');

      var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting), $proto = self.$$prototype;

      $proto.step_arg2 = $proto.receiver_num = $proto.step_arg1 = $proto.step = $proto.range = $proto.topfx = $proto.bypfx = $proto.creation_method = $proto.skipped_arg = nil;
      
      Opal.prop(self.$$prototype, '$$is_arithmetic_seq', true);
      var inf = Infinity;
      
      $def(self, '$initialize', function $$initialize(range, step, creation_method) {
        var $a, self = this, $ret_or_1 = nil;
        if (creation_method == null) creation_method = "step";
        self.creation_method = creation_method;
        if ($truthy(range['$is_a?']($$$('Array')))) {
          
          $a = [].concat($to_a(range)), (self.step_arg1 = ($a[0] == null ? nil : $a[0])), (self.step_arg2 = ($a[1] == null ? nil : $a[1])), (self.topfx = ($a[2] == null ? nil : $a[2])), (self.bypfx = ($a[3] == null ? nil : $a[3]));
          self.receiver_num = step;
          self.step = 1;
          self.range = ($truthy(self.step_arg2) ? (((self.step = self.step_arg2), Opal.Range.$new(self.receiver_num, self.step_arg1, false))) : ($truthy(self.step_arg1) ? (Opal.Range.$new(self.receiver_num, self.step_arg1, false)) : (Opal.Range.$new(self.receiver_num, nil, false))));
        } else {
          
          if (!$truthy(step)) {
            self.skipped_arg = true;
          }          $a = [range, ($truthy(($ret_or_1 = step)) ? ($ret_or_1) : (1))], (self.range = $a[0]), (self.step = $a[1]);
        }        self.object = self;
        if ($eqeq(self.step, 0)) {
          $Kernel.$raise($$('ArgumentError'), "step can't be 0");
        }        if ($truthy(self.step['$respond_to?']("to_int"))) {
          return nil
        } else {
          return $Kernel.$raise($$('ArgumentError'), "" + ("no implicit conversion of " + (self.step.$class()) + " ") + "into Integer")
        }      }, -2);
      self.$attr_reader("step");
      
      $def(self, '$begin', function $$begin() {
        var self = this;

        return self.range.$begin()
      });
      
      $def(self, '$end', function $$end() {
        var self = this;

        return self.range.$end()
      });
      
      $def(self, '$exclude_end?', function $ArithmeticSequence_exclude_end$ques$1() {
        var self = this;

        return self.range['$exclude_end?']()
      });
      
      $def(self, '$_lesser_than_end?', function $ArithmeticSequence__lesser_than_end$ques$2(val) {
        var self = this, end_ = nil, $ret_or_1 = nil;

        
        end_ = ($truthy(($ret_or_1 = self.$end())) ? ($ret_or_1) : (inf));
        if ($truthy($rb_gt(self.$step(), 0))) {
          if ($truthy(self['$exclude_end?']())) {
            return $rb_lt(val, end_)
          } else {
            return $rb_le(val, end_)
          }
        } else if ($truthy(self['$exclude_end?']())) {
          return $rb_gt(val, end_)
        } else {
          return $rb_ge(val, end_)
        }      });
      
      $def(self, '$_greater_than_begin?', function $ArithmeticSequence__greater_than_begin$ques$3(val) {
        var self = this, begin_ = nil, $ret_or_1 = nil;

        
        begin_ = ($truthy(($ret_or_1 = self.$begin())) ? ($ret_or_1) : ((inf)['$-@']()));
        if ($truthy($rb_gt(self.$step(), 0))) {
          return $rb_gt(val, begin_)
        } else {
          return $rb_lt(val, begin_)
        }      });
      
      $def(self, '$first', function $$first(count) {
        var self = this, iter = nil, $ret_or_1 = nil, out = nil;
        iter = ($truthy(($ret_or_1 = self.$begin())) ? ($ret_or_1) : ((inf)['$-@']()));
        if (!$truthy(count)) {
          return ($truthy(self['$_lesser_than_end?'](iter)) ? (iter) : (nil))
        }        out = [];
        while ($truthy(($truthy(($ret_or_1 = self['$_lesser_than_end?'](iter))) ? ($rb_gt(count, 0)) : ($ret_or_1)))) {
        
          out['$<<'](iter);
          iter = $rb_plus(iter, self.$step());
          count = $rb_minus(count, 1);
        }        return out;
      }, -1);
      
      $def(self, '$each', function $$each() {
        var block = $$each.$$p || nil, self = this, $ret_or_1 = nil, iter = nil;

        $$each.$$p = null;
        if (!(block !== nil)) {
          return self
        }        if ($eqeqeq(nil, ($ret_or_1 = self.$begin()))) {
          $Kernel.$raise($$('TypeError'), "nil can't be coerced into Integer");
        }        iter = ($truthy(($ret_or_1 = self.$begin())) ? ($ret_or_1) : ((inf)['$-@']()));
        while ($truthy(self['$_lesser_than_end?'](iter))) {
        
          Opal.yield1(block, iter);
          iter = $rb_plus(iter, self.$step());
        }        return self;
      });
      
      $def(self, '$last', function $$last(count) {
        var self = this, $ret_or_1 = nil, iter = nil, out = nil;
        if (($eqeqeq(inf, ($ret_or_1 = self.$end())) || ($eqeqeq((inf)['$-@'](), $ret_or_1)))) {
          $Kernel.$raise($$$('FloatDomainError'), self.$end());
        } else if ($eqeqeq(nil, $ret_or_1)) {
          $Kernel.$raise($$$('RangeError'), "cannot get the last element of endless arithmetic sequence");
        } else ;        iter = $rb_minus(self.$end(), $rb_minus(self.$end(), self.$begin())['$%'](self.$step()));
        if (!$truthy(self['$_lesser_than_end?'](iter))) {
          iter = $rb_minus(iter, self.$step());
        }        if (!$truthy(count)) {
          return ($truthy(self['$_greater_than_begin?'](iter)) ? (iter) : (nil))
        }        out = [];
        while ($truthy(($truthy(($ret_or_1 = self['$_greater_than_begin?'](iter))) ? ($rb_gt(count, 0)) : ($ret_or_1)))) {
        
          out['$<<'](iter);
          iter = $rb_minus(iter, self.$step());
          count = $rb_minus(count, 1);
        }        return out.$reverse();
      }, -1);
      
      $def(self, '$size', function $$size() {
        var self = this, step_sign = nil, iter = nil;

        
        step_sign = ($truthy($rb_gt(self.$step(), 0)) ? (1) : (-1));
        if ($not(self['$_lesser_than_end?'](self.$begin()))) {
          return 0
        } else if ($truthy([(inf)['$-@'](), inf]['$include?'](self.$step()))) {
          return 1
        } else if (($truthy([$rb_times((inf)['$-@'](), step_sign), nil]['$include?'](self.$begin())) || ($truthy([$rb_times(inf, step_sign), nil]['$include?'](self.$end()))))) {
          return inf;
        } else {
          
          iter = $rb_minus(self.$end(), $rb_minus(self.$end(), self.$begin())['$%'](self.$step()));
          if (!$truthy(self['$_lesser_than_end?'](iter))) {
            iter = $rb_minus(iter, self.$step());
          }          return $rb_plus($rb_divide($rb_minus(iter, self.$begin()), self.$step()).$abs().$to_i(), 1);
        }      });
      
      $def(self, '$==', function $ArithmeticSequence_$eq_eq$4(other) {
        var self = this, $ret_or_1 = nil, $ret_or_2 = nil, $ret_or_3 = nil, $ret_or_4 = nil;

        if ($truthy(($ret_or_1 = ($truthy(($ret_or_2 = ($truthy(($ret_or_3 = ($truthy(($ret_or_4 = self.$class()['$=='](other.$class()))) ? (self.$begin()['$=='](other.$begin())) : ($ret_or_4)))) ? (self.$end()['$=='](other.$end())) : ($ret_or_3)))) ? (self.$step()['$=='](other.$step())) : ($ret_or_2))))) {
          return self['$exclude_end?']()['$=='](other['$exclude_end?']())
        } else {
          return $ret_or_1
        }
      });
      
      $def(self, '$hash', function $$hash() {
        var self = this;

        return [self.$begin(), self.$end(), self.$step(), self['$exclude_end?']()].$hash()
      });
      
      $def(self, '$inspect', function $$inspect() {
        var self = this, args = nil;

        if ($truthy(self.receiver_num)) {
          
          args = ($truthy(self.step_arg2) ? ("(" + (self.topfx) + (self.step_arg1.$inspect()) + ", " + (self.bypfx) + (self.step_arg2.$inspect()) + ")") : ($truthy(self.step_arg1) ? ("(" + (self.topfx) + (self.step_arg1.$inspect()) + ")") : nil));
          return "(" + (self.receiver_num.$inspect()) + "." + (self.creation_method) + (args) + ")";
        } else {
          
          args = ($truthy(self.skipped_arg) ? (nil) : ("(" + (self.step) + ")"));
          return "((" + (self.range.$inspect()) + ")." + (self.creation_method) + (args) + ")";
        }
      });
      $alias(self, "===", "==");
      return $alias(self, "eql?", "==");
    })(self, self, $nesting)
  })('::', null, $nesting)
};

Opal.modules["corelib/enumerator/chain"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $deny_frozen_access = Opal.deny_frozen_access, $klass = Opal.klass, $slice = Opal.slice, $def = Opal.def, $send = Opal.send, $to_a = Opal.to_a, $truthy = Opal.truthy, $rb_plus = Opal.rb_plus, $thrower = Opal.thrower, nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('to_enum,size,each,<<,to_proc,include?,+,reverse_each,respond_to?,rewind,inspect');
  return (function($base, $super) {
    var self = $klass($base, $super, 'Enumerator');

    
    return (function($base, $super) {
      var self = $klass($base, $super, 'Chain');

      var $proto = self.$$prototype;

      $proto.enums = $proto.iterated = nil;
      
      
      $def(self, '$initialize', function $$initialize($a) {
        var $post_args, enums, self = this;

        
        $post_args = $slice(arguments);
        enums = $post_args;
        $deny_frozen_access(self);
        self.enums = enums;
        self.iterated = [];
        return (self.object = self);
      }, -1);
      
      $def(self, '$each', function $$each($a) {
        var block = $$each.$$p || nil, $post_args, args, self = this;

        $$each.$$p = null;
        $post_args = $slice(arguments);
        args = $post_args;
        if (!(block !== nil)) {
          return $send(self, 'to_enum', ["each"].concat($to_a(args)), function $$1(){var self = $$1.$$s == null ? this : $$1.$$s;

            return self.$size()}, {$$s: self})
        }        $send(self.enums, 'each', [], function $$2(enum$){var self = $$2.$$s == null ? this : $$2.$$s;
          if (self.iterated == null) self.iterated = nil;

          
          if (enum$ == null) enum$ = nil;
          self.iterated['$<<'](enum$);
          return $send(enum$, 'each', $to_a(args), block.$to_proc());}, {$$s: self});
        return self;
      }, -1);
      
      $def(self, '$size', function $$size($a) {try { var $t_return = $thrower('return'); 
        var $post_args, args, self = this, accum = nil;

        
        $post_args = $slice(arguments);
        args = $post_args;
        accum = 0;
        $send(self.enums, 'each', [], function $$3(enum$){var size = nil;

          
          if (enum$ == null) enum$ = nil;
          size = $send(enum$, 'size', $to_a(args));
          if ($truthy([nil, $$$($$$('Float'), 'INFINITY')]['$include?'](size))) {
            $t_return.$throw(size);
          };
          return (accum = $rb_plus(accum, size));}, {$$ret: $t_return});
        return accum;} catch($e) {
          if ($e === $t_return) return $e.$v;
          throw $e;
        }
      }, -1);
      
      $def(self, '$rewind', function $$rewind() {
        var self = this;

        
        $send(self.iterated, 'reverse_each', [], function $$4(enum$){
          
          if (enum$ == null) enum$ = nil;
          if ($truthy(enum$['$respond_to?']("rewind"))) {
            return enum$.$rewind()
          } else {
            return nil
          }});
        self.iterated = [];
        return self;
      });
      return $def(self, '$inspect', function $$inspect() {
        var self = this;

        return "#<Enumerator::Chain: " + (self.enums.$inspect()) + ">"
      });
    })(self, self)
  })('::', null)
};

Opal.modules["corelib/enumerator/generator"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $deny_frozen_access = Opal.deny_frozen_access, $klass = Opal.klass, $truthy = Opal.truthy, $Kernel = Opal.Kernel, $def = Opal.def, $slice = Opal.slice, $send = Opal.send, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('include,raise,new,to_proc');
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Enumerator');

    var $nesting = [self].concat($parent_nesting);

    return (function($base, $super, $parent_nesting) {
      var self = $klass($base, $super, 'Generator');

      var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting), $proto = self.$$prototype;

      $proto.block = nil;
      
      self.$include($$$('Enumerable'));
      
      $def(self, '$initialize', function $$initialize() {
        var block = $$initialize.$$p || nil, self = this;

        $$initialize.$$p = null;
        $deny_frozen_access(self);
        if (!$truthy(block)) {
          $Kernel.$raise($$$('LocalJumpError'), "no block given");
        }        return (self.block = block);
      });
      return $def(self, '$each', function $$each($a) {
        var block = $$each.$$p || nil, $post_args, args, self = this, yielder = nil;

        $$each.$$p = null;
        $post_args = $slice(arguments);
        args = $post_args;
        yielder = $send($$('Yielder'), 'new', [], block.$to_proc());
        
        try {
          args.unshift(yielder);

          Opal.yieldX(self.block, args);
        }
        catch (e) {
          if (e && e.$thrower_type == "breaker") {
            return e.$v;
          }
          else {
            throw e;
          }
        }
        return self;
      }, -1);
    })($nesting[0], null, $nesting)
  })($nesting[0], null, $nesting)
};

Opal.modules["corelib/enumerator/lazy"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $truthy = Opal.truthy, $coerce_to = Opal.coerce_to, $yield1 = Opal.yield1, $yieldX = Opal.yieldX, $deny_frozen_access = Opal.deny_frozen_access, $klass = Opal.klass, $slice = Opal.slice, $send2 = Opal.send2, $find_super = Opal.find_super, $to_a = Opal.to_a, $defs = Opal.defs, $Kernel = Opal.Kernel, $send = Opal.send, $def = Opal.def, $return_self = Opal.return_self, $Opal = Opal.Opal, $rb_lt = Opal.rb_lt, $eqeqeq = Opal.eqeqeq, $rb_plus = Opal.rb_plus, $alias = Opal.alias, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('raise,each,new,enumerator_size,yield,respond_to?,try_convert,<,===,+,for,class,to_proc,destructure,inspect,to_a,find_all,collect_concat,collect,enum_for');
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Enumerator');

    var $nesting = [self].concat($parent_nesting);

    return (function($base, $super, $parent_nesting) {
      var self = $klass($base, $super, 'Lazy');

      var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting), $proto = self.$$prototype;

      $proto.enumerator = nil;
      
      $klass(self, $$$('Exception'), 'StopLazyError');
      $defs(self, '$for', function $Lazy_for$1(object, $a) {
        var $post_args, $fwd_rest, $yield = $Lazy_for$1.$$p || nil, self = this, lazy = nil;

        $Lazy_for$1.$$p = null;
        
        $post_args = $slice(arguments, 1);
        $fwd_rest = $post_args;
        lazy = $send2(self, $find_super(self, 'for', $Lazy_for$1, false, true), 'for', [object].concat($to_a($fwd_rest)), $yield);
        lazy.enumerator = object;
        return lazy;
      }, -2);
      
      $def(self, '$initialize', function $$initialize(object, size) {
        var block = $$initialize.$$p || nil, self = this;

        $$initialize.$$p = null;
        if (size == null) size = nil;
        $deny_frozen_access(self);
        if (!(block !== nil)) {
          $Kernel.$raise($$$('ArgumentError'), "tried to call lazy new without a block");
        }        self.enumerator = object;
        return $send2(self, $find_super(self, 'initialize', $$initialize, false, true), 'initialize', [size], function $$2(yielder, $a){var $post_args, each_args;

          
          if (yielder == null) yielder = nil;
          $post_args = $slice(arguments, 1);
          each_args = $post_args;
          try {
            return $send(object, 'each', $to_a(each_args), function $$3($b){var $post_args, args;

              
              $post_args = $slice(arguments);
              args = $post_args;
              
            args.unshift(yielder);

            $yieldX(block, args);
          ;}, -1)
          } catch ($err) {
            if (Opal.rescue($err, [$$('StopLazyError')])) {
              try {
                return nil
              } finally { Opal.pop_exception(); }
            } else { throw $err; }
          }}, -2);
      }, -2);
      
      $def(self, '$lazy', $return_self);
      
      $def(self, '$collect', function $$collect() {
        var block = $$collect.$$p || nil, self = this;

        $$collect.$$p = null;
        if (!$truthy(block)) {
          $Kernel.$raise($$$('ArgumentError'), "tried to call lazy map without a block");
        }        return $send($$('Lazy'), 'new', [self, self.$enumerator_size()], function $$4(enum$, $a){var $post_args, args;

          
          if (enum$ == null) enum$ = nil;
          $post_args = $slice(arguments, 1);
          args = $post_args;
          
          var value = $yieldX(block, args);

          enum$.$yield(value);
}, -2);
      });
      
      $def(self, '$collect_concat', function $$collect_concat() {
        var block = $$collect_concat.$$p || nil, self = this;

        $$collect_concat.$$p = null;
        if (!$truthy(block)) {
          $Kernel.$raise($$$('ArgumentError'), "tried to call lazy map without a block");
        }        return $send($$('Lazy'), 'new', [self, nil], function $$5(enum$, $a){var $post_args, args;

          
          if (enum$ == null) enum$ = nil;
          $post_args = $slice(arguments, 1);
          args = $post_args;
          
          var value = $yieldX(block, args);

          if ((value)['$respond_to?']("force") && (value)['$respond_to?']("each")) {
            $send((value), 'each', [], function $$6(v){
            
            if (v == null) v = nil;
            return enum$.$yield(v);});
          }
          else {
            var array = $Opal.$try_convert(value, $$$('Array'), "to_ary");

            if (array === nil) {
              enum$.$yield(value);
            }
            else {
              $send((value), 'each', [], function $$7(v){
            
            if (v == null) v = nil;
            return enum$.$yield(v);});
            }
          }
}, -2);
      });
      
      $def(self, '$drop', function $$drop(n) {
        var self = this, current_size = nil, set_size = nil, dropped = nil;

        
        n = $coerce_to(n, $$$('Integer'), 'to_int');
        if ($truthy($rb_lt(n, 0))) {
          $Kernel.$raise($$$('ArgumentError'), "attempt to drop negative size");
        }        current_size = self.$enumerator_size();
        set_size = ($eqeqeq($$$('Integer'), current_size) ? (($truthy($rb_lt(n, current_size)) ? (n) : (current_size))) : (current_size));
        dropped = 0;
        return $send($$('Lazy'), 'new', [self, set_size], function $$8(enum$, $a){var $post_args, args;

          
          if (enum$ == null) enum$ = nil;
          $post_args = $slice(arguments, 1);
          args = $post_args;
          if ($truthy($rb_lt(dropped, n))) {
            return (dropped = $rb_plus(dropped, 1))
          } else {
            return $send(enum$, 'yield', $to_a(args))
          }}, -2);
      });
      
      $def(self, '$drop_while', function $$drop_while() {
        var block = $$drop_while.$$p || nil, self = this, succeeding = nil;

        $$drop_while.$$p = null;
        if (!$truthy(block)) {
          $Kernel.$raise($$$('ArgumentError'), "tried to call lazy drop_while without a block");
        }        succeeding = true;
        return $send($$('Lazy'), 'new', [self, nil], function $$9(enum$, $a){var $post_args, args;

          
          if (enum$ == null) enum$ = nil;
          $post_args = $slice(arguments, 1);
          args = $post_args;
          if ($truthy(succeeding)) {
            
            var value = $yieldX(block, args);

            if (!$truthy(value)) {
              succeeding = false;

              $send(enum$, 'yield', $to_a(args));
            }
          
          } else {
            return $send(enum$, 'yield', $to_a(args))
          }}, -2);
      });
      
      $def(self, '$enum_for', function $$enum_for($a, $b) {
        var block = $$enum_for.$$p || nil, $post_args, method, args, self = this;

        $$enum_for.$$p = null;
        $post_args = $slice(arguments);
        
        if ($post_args.length > 0) method = $post_args.shift();if (method == null) method = "each";
        args = $post_args;
        return $send(self.$class(), 'for', [self, method].concat($to_a(args)), block.$to_proc());
      }, -1);
      
      $def(self, '$find_all', function $$find_all() {
        var block = $$find_all.$$p || nil, self = this;

        $$find_all.$$p = null;
        if (!$truthy(block)) {
          $Kernel.$raise($$$('ArgumentError'), "tried to call lazy select without a block");
        }        return $send($$('Lazy'), 'new', [self, nil], function $$10(enum$, $a){var $post_args, args;

          
          if (enum$ == null) enum$ = nil;
          $post_args = $slice(arguments, 1);
          args = $post_args;
          
          var value = $yieldX(block, args);

          if ($truthy(value)) {
            $send(enum$, 'yield', $to_a(args));
          }
}, -2);
      });
      
      $def(self, '$grep', function $$grep(pattern) {
        var block = $$grep.$$p || nil, self = this;

        $$grep.$$p = null;
        if ($truthy(block)) {
          return $send($$('Lazy'), 'new', [self, nil], function $$11(enum$, $a){var $post_args, args;

            
            if (enum$ == null) enum$ = nil;
            $post_args = $slice(arguments, 1);
            args = $post_args;
            
            var param = $Opal.$destructure(args),
                value = pattern['$==='](param);

            if ($truthy(value)) {
              value = $yield1(block, param);

              enum$.$yield($yield1(block, param));
            }
}, -2)
        } else {
          return $send($$('Lazy'), 'new', [self, nil], function $$12(enum$, $a){var $post_args, args;

            
            if (enum$ == null) enum$ = nil;
            $post_args = $slice(arguments, 1);
            args = $post_args;
            
            var param = $Opal.$destructure(args),
                value = pattern['$==='](param);

            if ($truthy(value)) {
              enum$.$yield(param);
            }
}, -2)
        }      });
      
      $def(self, '$reject', function $$reject() {
        var block = $$reject.$$p || nil, self = this;

        $$reject.$$p = null;
        if (!$truthy(block)) {
          $Kernel.$raise($$$('ArgumentError'), "tried to call lazy reject without a block");
        }        return $send($$('Lazy'), 'new', [self, nil], function $$13(enum$, $a){var $post_args, args;

          
          if (enum$ == null) enum$ = nil;
          $post_args = $slice(arguments, 1);
          args = $post_args;
          
          var value = $yieldX(block, args);

          if (!$truthy(value)) {
            $send(enum$, 'yield', $to_a(args));
          }
}, -2);
      });
      
      $def(self, '$take', function $$take(n) {
        var self = this, current_size = nil, set_size = nil, taken = nil;

        
        n = $coerce_to(n, $$$('Integer'), 'to_int');
        if ($truthy($rb_lt(n, 0))) {
          $Kernel.$raise($$$('ArgumentError'), "attempt to take negative size");
        }        current_size = self.$enumerator_size();
        set_size = ($eqeqeq($$$('Integer'), current_size) ? (($truthy($rb_lt(n, current_size)) ? (n) : (current_size))) : (current_size));
        taken = 0;
        return $send($$('Lazy'), 'new', [self, set_size], function $$14(enum$, $a){var $post_args, args;

          
          if (enum$ == null) enum$ = nil;
          $post_args = $slice(arguments, 1);
          args = $post_args;
          if ($truthy($rb_lt(taken, n))) {
            
            $send(enum$, 'yield', $to_a(args));
            return (taken = $rb_plus(taken, 1));
          } else {
            return $Kernel.$raise($$('StopLazyError'))
          }}, -2);
      });
      
      $def(self, '$take_while', function $$take_while() {
        var block = $$take_while.$$p || nil, self = this;

        $$take_while.$$p = null;
        if (!$truthy(block)) {
          $Kernel.$raise($$$('ArgumentError'), "tried to call lazy take_while without a block");
        }        return $send($$('Lazy'), 'new', [self, nil], function $$15(enum$, $a){var $post_args, args;

          
          if (enum$ == null) enum$ = nil;
          $post_args = $slice(arguments, 1);
          args = $post_args;
          
          var value = $yieldX(block, args);

          if ($truthy(value)) {
            $send(enum$, 'yield', $to_a(args));
          }
          else {
            $Kernel.$raise($$('StopLazyError'));
          }
}, -2);
      });
      
      $def(self, '$inspect', function $$inspect() {
        var self = this;

        return "#<" + (self.$class()) + ": " + (self.enumerator.$inspect()) + ">"
      });
      $alias(self, "force", "to_a");
      $alias(self, "filter", "find_all");
      $alias(self, "flat_map", "collect_concat");
      $alias(self, "map", "collect");
      $alias(self, "select", "find_all");
      return $alias(self, "to_enum", "enum_for");
    })(self, self, $nesting)
  })('::', null, $nesting)
};

Opal.modules["corelib/enumerator/yielder"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $def = Opal.def, $slice = Opal.slice, $send = Opal.send, $to_a = Opal.to_a, $nesting = [], nil = Opal.nil;

  Opal.add_stubs('yield,proc');
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Enumerator');

    var $nesting = [self].concat($parent_nesting);

    return (function($base, $super) {
      var self = $klass($base, $super, 'Yielder');

      var $proto = self.$$prototype;

      $proto.block = nil;
      
      
      $def(self, '$initialize', function $$initialize() {
        var block = $$initialize.$$p || nil, self = this;

        $$initialize.$$p = null;
        self.block = block;
        return self;
      });
      
      $def(self, '$yield', function $Yielder_yield$1($a) {
        var $post_args, values, self = this;

        
        $post_args = $slice(arguments);
        values = $post_args;
        
        var value = Opal.yieldX(self.block, values);

        if (value && value.$thrower_type == "break") {
          throw value;
        }

        return value;
      }, -1);
      
      $def(self, '$<<', function $Yielder_$lt$lt$2(value) {
        var self = this;

        
        self.$yield(value);
        return self;
      });
      return $def(self, '$to_proc', function $$to_proc() {
        var self = this;

        return $send(self, 'proc', [], function $$3($a){var $post_args, values, self = $$3.$$s == null ? this : $$3.$$s;

          
          $post_args = $slice(arguments);
          values = $post_args;
          return $send(self, 'yield', $to_a(values));}, {$$arity: -1, $$s: self})
      });
    })($nesting[0], null)
  })($nesting[0], null, $nesting)
};

Opal.modules["corelib/enumerator"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $slice = Opal.slice, $coerce_to = Opal.coerce_to, $deny_frozen_access = Opal.deny_frozen_access, $klass = Opal.klass, $defs = Opal.defs, $truthy = Opal.truthy, $send = Opal.send, $not = Opal.not, $def = Opal.def, $rb_plus = Opal.rb_plus, $to_a = Opal.to_a, $Opal = Opal.Opal, $send2 = Opal.send2, $find_super = Opal.find_super, $rb_ge = Opal.rb_ge, $Kernel = Opal.Kernel, $rb_le = Opal.rb_le, $alias = Opal.alias, self = Opal.top, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('require,include,allocate,new,to_proc,!,respond_to?,empty?,nil?,+,class,__send__,call,enum_for,size,destructure,map,>=,length,raise,[],peek_values,<=,next_values,inspect,any?,each_with_object,autoload');
  
  self.$require("corelib/enumerable");
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Enumerator');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting), $proto = self.$$prototype;

    $proto.size = $proto.args = $proto.object = $proto.method = $proto.values = $proto.cursor = nil;
    
    self.$include($$$('Enumerable'));
    self.$$prototype.$$is_enumerator = true;
    $defs(self, '$for', function $Enumerator_for$1(object, $a, $b) {
      var block = $Enumerator_for$1.$$p || nil, $post_args, method, args, self = this;

      $Enumerator_for$1.$$p = null;
      $post_args = $slice(arguments, 1);
      
      if ($post_args.length > 0) method = $post_args.shift();if (method == null) method = "each";
      args = $post_args;
      
      var obj = self.$allocate();

      obj.object = object;
      obj.size   = block;
      obj.method = method;
      obj.args   = args;
      obj.cursor = 0;

      return obj;
    }, -2);
    
    $def(self, '$initialize', function $$initialize($a) {
      var block = $$initialize.$$p || nil, self = this;

      $$initialize.$$p = null;
      $slice(arguments);
      $deny_frozen_access(self);
      self.cursor = 0;
      if ($truthy(block)) {
        
        self.object = $send($$('Generator'), 'new', [], block.$to_proc());
        self.method = "each";
        self.args = [];
        self.size = arguments[0] || nil;
        if (($truthy(self.size) && ($not(self.size['$respond_to?']("call"))))) {
          return (self.size = $coerce_to(self.size, $$$('Integer'), 'to_int'))
        } else {
          return nil
        }      } else {
        
        self.object = arguments[0];
        self.method = arguments[1] || "each";
        self.args = $slice(arguments, 2);
        return (self.size = nil);
      }    }, -1);
    
    $def(self, '$each', function $$each($a) {
      var block = $$each.$$p || nil, $post_args, args, self = this;

      $$each.$$p = null;
      $post_args = $slice(arguments);
      args = $post_args;
      if (($truthy(block['$nil?']()) && ($truthy(args['$empty?']())))) {
        return self
      }      args = $rb_plus(self.args, args);
      if ($truthy(block['$nil?']())) {
        return $send(self.$class(), 'new', [self.object, self.method].concat($to_a(args)))
      }      return $send(self.object, '__send__', [self.method].concat($to_a(args)), block.$to_proc());
    }, -1);
    
    $def(self, '$size', function $$size() {
      var self = this;

      if ($truthy(self.size['$respond_to?']("call"))) {
        return $send(self.size, 'call', $to_a(self.args))
      } else {
        return self.size
      }
    });
    
    $def(self, '$with_index', function $$with_index(offset) {
      var block = $$with_index.$$p || nil, self = this;

      $$with_index.$$p = null;
      if (offset == null) offset = 0;
      offset = ($truthy(offset) ? ($coerce_to(offset, $$$('Integer'), 'to_int')) : (0));
      if (!$truthy(block)) {
        return $send(self, 'enum_for', ["with_index", offset], function $$2(){var self = $$2.$$s == null ? this : $$2.$$s;

          return self.$size()}, {$$s: self})
      }      
      var index = offset;

      self.$each.$$p = function() {
        var param = $Opal.$destructure(arguments),
            value = block(param, index);

        index++;

        return value;
      };

      return self.$each();
    }, -1);
    
    $def(self, '$each_with_index', function $$each_with_index() {
      var block = $$each_with_index.$$p || nil, self = this;

      $$each_with_index.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["each_with_index"], function $$3(){var self = $$3.$$s == null ? this : $$3.$$s;

          return self.$size()}, {$$s: self})
      }      $send2(self, $find_super(self, 'each_with_index', $$each_with_index, false, true), 'each_with_index', [], block);
      return self.object;
    });
    
    $def(self, '$rewind', function $$rewind() {
      var self = this;

      
      self.cursor = 0;
      return self;
    });
    
    $def(self, '$peek_values', function $$peek_values() {
      var self = this, $ret_or_1 = nil;

      
      self.values = ($truthy(($ret_or_1 = self.values)) ? ($ret_or_1) : ($send(self, 'map', [], function $$4($a){var $post_args, i;

        
        $post_args = $slice(arguments);
        i = $post_args;
        return i;}, -1)));
      if ($truthy($rb_ge(self.cursor, self.values.$length()))) {
        $Kernel.$raise($$$('StopIteration'), "iteration reached an end");
      }      return self.values['$[]'](self.cursor);
    });
    
    $def(self, '$peek', function $$peek() {
      var self = this, values = nil;

      
      values = self.$peek_values();
      if ($truthy($rb_le(values.$length(), 1))) {
        return values['$[]'](0)
      } else {
        return values
      }    });
    
    $def(self, '$next_values', function $$next_values() {
      var self = this, out = nil;

      
      out = self.$peek_values();
      self.cursor = $rb_plus(self.cursor, 1);
      return out;
    });
    
    $def(self, '$next', function $$next() {
      var self = this, values = nil;

      
      values = self.$next_values();
      if ($truthy($rb_le(values.$length(), 1))) {
        return values['$[]'](0)
      } else {
        return values
      }    });
    
    $def(self, '$feed', function $$feed(arg) {
      var self = this;

      return self.$raise($$('NotImplementedError'), "Opal doesn't support Enumerator#feed")
    });
    
    $def(self, '$+', function $Enumerator_$plus$5(other) {
      var self = this;

      return $$$($$$('Enumerator'), 'Chain').$new(self, other)
    });
    
    $def(self, '$inspect', function $$inspect() {
      var self = this, result = nil;

      
      result = "#<" + (self.$class()) + ": " + (self.object.$inspect()) + ":" + (self.method);
      if ($truthy(self.args['$any?']())) {
        result = $rb_plus(result, "(" + (self.args.$inspect()['$[]']($$$('Range').$new(1, -2))) + ")");
      }      return $rb_plus(result, ">");
    });
    $alias(self, "with_object", "each_with_object");
    self.$autoload("ArithmeticSequence", "corelib/enumerator/arithmetic_sequence");
    self.$autoload("Chain", "corelib/enumerator/chain");
    self.$autoload("Generator", "corelib/enumerator/generator");
    self.$autoload("Lazy", "corelib/enumerator/lazy");
    return self.$autoload("Yielder", "corelib/enumerator/yielder");
  })('::', null, $nesting);
};

Opal.modules["corelib/numeric"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $truthy = Opal.truthy, $Kernel = Opal.Kernel, $def = Opal.def, $to_ary = Opal.to_ary, $return_self = Opal.return_self, $rb_minus = Opal.rb_minus, $rb_times = Opal.rb_times, $rb_lt = Opal.rb_lt, $eqeq = Opal.eqeq, $rb_divide = Opal.rb_divide, $return_val = Opal.return_val, $Opal = Opal.Opal, $slice = Opal.slice, $extract_kwargs = Opal.extract_kwargs, $ensure_kwargs = Opal.ensure_kwargs, $not = Opal.not, $send = Opal.send, $rb_ge = Opal.rb_ge, $rb_le = Opal.rb_le, $rb_plus = Opal.rb_plus, $rb_gt = Opal.rb_gt, $alias = Opal.alias, self = Opal.top, nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('require,include,instance_of?,class,Float,respond_to?,coerce,__send__,raise,equal?,-,*,div,<,-@,ceil,to_f,denominator,to_r,==,floor,/,%,Complex,zero?,numerator,abs,arg,coerce_to!,round,<=>,compare,is_a?,!,new,enum_for,to_proc,negative?,>=,<=,+,to_i,truncate,>,angle,conj,imag,rect');
  
  self.$require("corelib/comparable");
  return (function($base, $super) {
    var self = $klass($base, $super, 'Numeric');

    
    
    self.$include($$$('Comparable'));
    
    $def(self, '$coerce', function $$coerce(other) {
      var self = this;

      
      if ($truthy(other['$instance_of?'](self.$class()))) {
        return [other, self]
      }      return [$Kernel.$Float(other), $Kernel.$Float(self)];
    });
    
    $def(self, '$__coerced__', function $$__coerced__(method, other) {
      var $a, $b, self = this, a = nil, b = nil;

      if ($truthy(other['$respond_to?']("coerce"))) {
        
        $b = other.$coerce(self), $a = $to_ary($b), (a = ($a[0] == null ? nil : $a[0])), (b = ($a[1] == null ? nil : $a[1]));
        return a.$__send__(method, b);
      } else 
      switch (method) {
        case "+":
        case "-":
        case "*":
        case "/":
        case "%":
        case "&":
        case "|":
        case "^":
        case "**":
          return $Kernel.$raise($$$('TypeError'), "" + (other.$class()) + " can't be coerced into Numeric")
        case ">":
        case ">=":
        case "<":
        case "<=":
        case "<=>":
          return $Kernel.$raise($$$('ArgumentError'), "comparison of " + (self.$class()) + " with " + (other.$class()) + " failed")
        default:
          return nil
      }
    });
    
    $def(self, '$<=>', function $Numeric_$lt_eq_gt$1(other) {
      var self = this;

      
      if ($truthy(self['$equal?'](other))) {
        return 0
      }      return nil;
    });
    
    $def(self, '$+@', $return_self);
    
    $def(self, '$-@', function $Numeric_$minus$$2() {
      var self = this;

      return $rb_minus(0, self)
    });
    
    $def(self, '$%', function $Numeric_$percent$3(other) {
      var self = this;

      return $rb_minus(self, $rb_times(other, self.$div(other)))
    });
    
    $def(self, '$abs', function $$abs() {
      var self = this;

      if ($rb_lt(self, 0)) {
        return self['$-@']()
      } else {
        return self
      }
    });
    
    $def(self, '$abs2', function $$abs2() {
      var self = this;

      return $rb_times(self, self)
    });
    
    $def(self, '$angle', function $$angle() {
      var self = this;

      if ($rb_lt(self, 0)) {
        return $$$($$$('Math'), 'PI')
      } else {
        return 0
      }
    });
    
    $def(self, '$ceil', function $$ceil(ndigits) {
      var self = this;

      
      if (ndigits == null) ndigits = 0;
      return self.$to_f().$ceil(ndigits);
    }, -1);
    
    $def(self, '$conj', $return_self);
    
    $def(self, '$denominator', function $$denominator() {
      var self = this;

      return self.$to_r().$denominator()
    });
    
    $def(self, '$div', function $$div(other) {
      var self = this;

      
      if ($eqeq(other, 0)) {
        $Kernel.$raise($$$('ZeroDivisionError'), "divided by o");
      }      return $rb_divide(self, other).$floor();
    });
    
    $def(self, '$divmod', function $$divmod(other) {
      var self = this;

      return [self.$div(other), self['$%'](other)]
    });
    
    $def(self, '$fdiv', function $$fdiv(other) {
      var self = this;

      return $rb_divide(self.$to_f(), other)
    });
    
    $def(self, '$floor', function $$floor(ndigits) {
      var self = this;

      
      if (ndigits == null) ndigits = 0;
      return self.$to_f().$floor(ndigits);
    }, -1);
    
    $def(self, '$i', function $$i() {
      var self = this;

      return $Kernel.$Complex(0, self)
    });
    
    $def(self, '$imag', $return_val(0));
    
    $def(self, '$integer?', $return_val(false));
    
    $def(self, '$nonzero?', function $Numeric_nonzero$ques$4() {
      var self = this;

      if ($truthy(self['$zero?']())) {
        return nil
      } else {
        return self
      }
    });
    
    $def(self, '$numerator', function $$numerator() {
      var self = this;

      return self.$to_r().$numerator()
    });
    
    $def(self, '$polar', function $$polar() {
      var self = this;

      return [self.$abs(), self.$arg()]
    });
    
    $def(self, '$quo', function $$quo(other) {
      var self = this;

      return $rb_divide($Opal['$coerce_to!'](self, $$$('Rational'), "to_r"), other)
    });
    
    $def(self, '$real', $return_self);
    
    $def(self, '$real?', $return_val(true));
    
    $def(self, '$rect', function $$rect() {
      var self = this;

      return [self, 0]
    });
    
    $def(self, '$round', function $$round(digits) {
      var self = this;
      return self.$to_f().$round(digits);
    }, -1);
    
    $def(self, '$step', function $$step($a, $b, $c) {
      var block = $$step.$$p || nil, $post_args, $kwargs, limit, step, to, by, self = this, counter = nil;

      $$step.$$p = null;
      $post_args = $slice(arguments);
      $kwargs = $extract_kwargs($post_args);
      $kwargs = $ensure_kwargs($kwargs);
      
      if ($post_args.length > 0) limit = $post_args.shift();      
      if ($post_args.length > 0) step = $post_args.shift();      
      to = $kwargs.$$smap["to"];      
      by = $kwargs.$$smap["by"];      
      if (limit !== undefined && to !== undefined) {
        $Kernel.$raise($$$('ArgumentError'), "to is given twice");
      }

      if (step !== undefined && by !== undefined) {
        $Kernel.$raise($$$('ArgumentError'), "step is given twice");
      }

      if (to !== undefined) {
        limit = to;
      }

      if (by !== undefined) {
        step = by;
      }

      if (limit === undefined) {
        limit = nil;
      }

      function validateParameters() {
        if (step === nil) {
          $Kernel.$raise($$$('TypeError'), "step must be numeric");
        }

        if (step != null && step['$=='](0)) {
          $Kernel.$raise($$$('ArgumentError'), "step can't be 0");
        }

        if (step === nil || step == null) {
          step = 1;
        }

        var sign = step['$<=>'](0);

        if (sign === nil) {
          $Kernel.$raise($$$('ArgumentError'), "0 can't be coerced into " + (step.$class()));
        }

        if (limit === nil || limit == null) {
          limit = sign > 0 ? $$$($$$('Float'), 'INFINITY') : $$$($$$('Float'), 'INFINITY')['$-@']();
        }

        $Opal.$compare(self, limit);
      }

      function stepFloatSize() {
        if ((step > 0 && self > limit) || (step < 0 && self < limit)) {
          return 0;
        } else if (step === Infinity || step === -Infinity) {
          return 1;
        } else {
          var abs = Math.abs, floor = Math.floor,
              err = (abs(self) + abs(limit) + abs(limit - self)) / abs(step) * $$$($$$('Float'), 'EPSILON');

          if (err === Infinity || err === -Infinity) {
            return 0;
          } else {
            if (err > 0.5) {
              err = 0.5;
            }

            return floor((limit - self) / step + err) + 1
          }
        }
      }

      function stepSize() {
        validateParameters();

        if (step === 0) {
          return Infinity;
        }

        if (step % 1 !== 0) {
          return stepFloatSize();
        } else if ((step > 0 && self > limit) || (step < 0 && self < limit)) {
          return 0;
        } else {
          var ceil = Math.ceil, abs = Math.abs,
              lhs = abs(self - limit) + 1,
              rhs = abs(step);

          return ceil(lhs / rhs);
        }
      }
      if (!(block !== nil)) {
        if ((($not(limit) || ($truthy(limit['$is_a?']($$$('Numeric'))))) && (($not(step) || ($truthy(step['$is_a?']($$$('Numeric')))))))) {
          return $$$($$$('Enumerator'), 'ArithmeticSequence').$new([limit, step, ($truthy(to) ? ("to: ") : nil), ($truthy(by) ? ("by: ") : nil)], self)
        } else {
          return $send(self, 'enum_for', ["step", limit, step], (stepSize).$to_proc())
        }
      }      
      validateParameters();

      var isDesc = step['$negative?'](),
          isInf = step['$=='](0) ||
                  (limit === Infinity && !isDesc) ||
                  (limit === -Infinity && isDesc);

      if (self.$$is_number && step.$$is_number && limit.$$is_number) {
        if (self % 1 === 0 && (isInf || limit % 1 === 0) && step % 1 === 0) {
          var value = self;

          if (isInf) {
            for (;; value += step) {
              block(value);
            }
          } else if (isDesc) {
            for (; value >= limit; value += step) {
              block(value);
            }
          } else {
            for (; value <= limit; value += step) {
              block(value);
            }
          }

          return self;
        } else {
          var begin = self.$to_f().valueOf();
          step = step.$to_f().valueOf();
          limit = limit.$to_f().valueOf();

          var n = stepFloatSize();

          if (!isFinite(step)) {
            if (n !== 0) block(begin);
          } else if (step === 0) {
            while (true) {
              block(begin);
            }
          } else {
            for (var i = 0; i < n; i++) {
              var d = i * step + self;
              if (step >= 0 ? limit < d : limit > d) {
                d = limit;
              }
              block(d);
            }
          }

          return self;
        }
      }
      counter = self;
      while ($truthy(isDesc ? $rb_ge(counter, limit) : $rb_le(counter, limit))) {
      
        Opal.yield1(block, counter);
        counter = $rb_plus(counter, step);
      }    }, -1);
    
    $def(self, '$to_c', function $$to_c() {
      var self = this;

      return $Kernel.$Complex(self, 0)
    });
    
    $def(self, '$to_int', function $$to_int() {
      var self = this;

      return self.$to_i()
    });
    
    $def(self, '$truncate', function $$truncate(ndigits) {
      var self = this;

      
      if (ndigits == null) ndigits = 0;
      return self.$to_f().$truncate(ndigits);
    }, -1);
    
    $def(self, '$zero?', function $Numeric_zero$ques$5() {
      var self = this;

      return self['$=='](0)
    });
    
    $def(self, '$positive?', function $Numeric_positive$ques$6() {
      var self = this;

      return $rb_gt(self, 0)
    });
    
    $def(self, '$negative?', function $Numeric_negative$ques$7() {
      var self = this;

      return $rb_lt(self, 0)
    });
    
    $def(self, '$dup', $return_self);
    
    $def(self, '$clone', function $$clone($kwargs) {
      var self = this;

      
      $kwargs = $ensure_kwargs($kwargs);
      
      $kwargs.$$smap["freeze"];      return self;
    }, -1);
    
    $def(self, '$finite?', $return_val(true));
    
    $def(self, '$infinite?', $return_val(nil));
    $alias(self, "arg", "angle");
    $alias(self, "conjugate", "conj");
    $alias(self, "imaginary", "imag");
    $alias(self, "magnitude", "abs");
    $alias(self, "modulo", "%");
    $alias(self, "phase", "arg");
    return $alias(self, "rectangular", "rect");
  })('::', null);
};

Opal.modules["corelib/array"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $truthy = Opal.truthy, $falsy = Opal.falsy, $hash_ids = Opal.hash_ids, $yield1 = Opal.yield1, $hash_get = Opal.hash_get, $hash_put = Opal.hash_put, $hash_delete = Opal.hash_delete, $coerce_to = Opal.coerce_to, $respond_to = Opal.respond_to, $deny_frozen_access = Opal.deny_frozen_access, $freeze = Opal.freeze, $klass = Opal.klass, $slice = Opal.slice, $defs = Opal.defs, $Kernel = Opal.Kernel, $def = Opal.def, $Opal = Opal.Opal, $eqeqeq = Opal.eqeqeq, $hash2 = Opal.hash2, $send2 = Opal.send2, $find_super = Opal.find_super, $send = Opal.send, $rb_gt = Opal.rb_gt, $rb_times = Opal.rb_times, $eqeq = Opal.eqeq, $rb_minus = Opal.rb_minus, $to_a = Opal.to_a, $to_ary = Opal.to_ary, $gvars = Opal.gvars, $rb_ge = Opal.rb_ge, $assign_ivar = Opal.assign_ivar, $rb_lt = Opal.rb_lt, $return_self = Opal.return_self, $neqeq = Opal.neqeq, $alias = Opal.alias, self = Opal.top, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('require,include,to_a,warn,raise,replace,respond_to?,to_ary,coerce_to?,===,join,to_str,hash,<=>,==,object_id,inspect,enum_for,class,bsearch_index,to_proc,nil?,coerce_to!,>,*,enumerator_size,empty?,size,map,equal?,dup,each,reduce,-,[],dig,eql?,length,exclude_end?,flatten,frozen?,__id__,&,!,intersection,to_s,new,item,max,min,>=,**,delete_if,reverse,rotate,rand,at,keep_if,shuffle!,<,sort,sort_by,!=,times,[]=,<<,uniq,|,values,is_a?,end,begin,upto,reject,push,select,select!,collect,collect!,unshift,pristine,singleton_class');
  
  self.$require("corelib/enumerable");
  self.$require("corelib/numeric");
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Array');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting);

    
    self.$include($$$('Enumerable'));
    Opal.prop(self.$$prototype, '$$is_array', true);
    
    // Recent versions of V8 (> 7.1) only use an optimized implementation when Array.prototype is unmodified.
    // For instance, "array-splice.tq" has a "fast path" (ExtractFastJSArray, defined in "src/codegen/code-stub-assembler.cc")
    // but it's only enabled when "IsPrototypeInitialArrayPrototype()" is true.
    //
    // Older versions of V8 were using relatively fast JS-with-extensions code even when Array.prototype is modified:
    // https://github.com/v8/v8/blob/7.0.1/src/js/array.js#L599-L642
    //
    // In short, Array operations are slow in recent versions of V8 when the Array.prototype has been tampered.
    // So, when possible, we are using faster open-coded version to boost the performance.

    // As of V8 8.4, depending on the size of the array, this is up to ~25x times faster than Array#shift()
    // Implementation is heavily inspired by: https://github.com/nodejs/node/blob/ba684805b6c0eded76e5cd89ee00328ac7a59365/lib/internal/util.js#L341-L347
    function shiftNoArg(list) {
      var r = list[0];
      var index = 1;
      var length = list.length;
      for (; index < length; index++) {
        list[index - 1] = list[index];
      }
      list.pop();
      return r;
    }

    function toArraySubclass(obj, klass) {
      if (klass.$$name === Opal.Array) {
        return obj;
      } else {
        return klass.$allocate().$replace((obj).$to_a());
      }
    }

    // A helper for keep_if and delete_if, filter is either Opal.truthy
    // or Opal.falsy.
    function filterIf(self, filter, block) {
      var value, raised = null, updated = new Array(self.length);

      for (var i = 0, i2 = 0, length = self.length; i < length; i++) {
        if (!raised) {
          try {
            value = $yield1(block, self[i]);
          } catch(error) {
            raised = error;
          }
        }

        if (raised || filter(value)) {
          updated[i2] = self[i];
          i2 += 1;
        }
      }

      if (i2 !== i) {
        self.splice.apply(self, [0, updated.length].concat(updated));
        self.splice(i2, updated.length);
      }

      if (raised) throw raised;
    }
    $defs(self, '$[]', function $Array_$$$1($a) {
      var $post_args, objects, self = this;

      
      $post_args = $slice(arguments);
      objects = $post_args;
      return toArraySubclass(objects, self);    }, -1);
    
    $def(self, '$initialize', function $$initialize(size, obj) {
      var block = $$initialize.$$p || nil, self = this;

      $$initialize.$$p = null;
      if (size == null) size = nil;
      if (obj == null) obj = nil;
      
      $deny_frozen_access(self);

      if (obj !== nil && block !== nil) {
        $Kernel.$warn("warning: block supersedes default value argument");
      }

      if (size > $$$($$$('Integer'), 'MAX')) {
        $Kernel.$raise($$$('ArgumentError'), "array size too big");
      }

      if (arguments.length > 2) {
        $Kernel.$raise($$$('ArgumentError'), "wrong number of arguments (" + (arguments.length) + " for 0..2)");
      }

      if (arguments.length === 0) {
        self.splice(0, self.length);
        return self;
      }

      if (arguments.length === 1) {
        if (size.$$is_array) {
          self.$replace(size.$to_a());
          return self;
        } else if (size['$respond_to?']("to_ary")) {
          self.$replace(size.$to_ary());
          return self;
        }
      }

      size = $coerce_to(size, $$$('Integer'), 'to_int');

      if (size < 0) {
        $Kernel.$raise($$$('ArgumentError'), "negative array size");
      }

      self.splice(0, self.length);
      var i, value;

      if (block === nil) {
        for (i = 0; i < size; i++) {
          self.push(obj);
        }
      }
      else {
        for (i = 0, value; i < size; i++) {
          value = block(i);
          self[i] = value;
        }
      }

      return self;
    }, -1);
    $defs(self, '$try_convert', function $$try_convert(obj) {
      
      return $Opal['$coerce_to?'](obj, $$$('Array'), "to_ary")
    });
    
    $def(self, '$&', function $Array_$$2(other) {
      var self = this;

      
      other = ($eqeqeq($$$('Array'), other) ? (other.$to_a()) : (($coerce_to(other, $$$('Array'), 'to_ary')).$to_a()));
      
      var result = [], hash = $hash2([], {}), i, length, item;

      for (i = 0, length = other.length; i < length; i++) {
        $hash_put(hash, other[i], true);
      }

      for (i = 0, length = self.length; i < length; i++) {
        item = self[i];
        if ($hash_delete(hash, item) !== undefined) {
          result.push(item);
        }
      }

      return result;
    });
    
    $def(self, '$|', function $Array_$$3(other) {
      var self = this;

      
      other = ($eqeqeq($$$('Array'), other) ? (other.$to_a()) : (($coerce_to(other, $$$('Array'), 'to_ary')).$to_a()));
      
      var hash = $hash2([], {}), i, length;

      for (i = 0, length = self.length; i < length; i++) {
        $hash_put(hash, self[i], true);
      }

      for (i = 0, length = other.length; i < length; i++) {
        $hash_put(hash, other[i], true);
      }

      return hash.$keys();
    });
    
    $def(self, '$*', function $Array_$$4(other) {
      var self = this;

      
      if ($truthy(other['$respond_to?']("to_str"))) {
        return self.$join(other.$to_str())
      }      other = $coerce_to(other, $$$('Integer'), 'to_int');
      if ($truthy(other < 0)) {
        $Kernel.$raise($$$('ArgumentError'), "negative argument");
      }      
      var result = [],
          converted = self.$to_a();

      for (var i = 0; i < other; i++) {
        result = result.concat(converted);
      }

      return result;
    });
    
    $def(self, '$+', function $Array_$plus$5(other) {
      var self = this;

      
      other = ($eqeqeq($$$('Array'), other) ? (other.$to_a()) : (($coerce_to(other, $$$('Array'), 'to_ary')).$to_a()));
      return self.concat(other);    });
    
    $def(self, '$-', function $Array_$minus$6(other) {
      var self = this;

      
      other = ($eqeqeq($$$('Array'), other) ? (other.$to_a()) : (($coerce_to(other, $$$('Array'), 'to_ary')).$to_a()));
      if ($truthy(self.length === 0)) {
        return []
      }      if ($truthy(other.length === 0)) {
        return self.slice()
      }      
      var result = [], hash = $hash2([], {}), i, length, item;

      for (i = 0, length = other.length; i < length; i++) {
        $hash_put(hash, other[i], true);
      }

      for (i = 0, length = self.length; i < length; i++) {
        item = self[i];
        if ($hash_get(hash, item) === undefined) {
          result.push(item);
        }
      }

      return result;
    });
    
    $def(self, '$<<', function $Array_$lt$lt$7(object) {
      var self = this;

      
      $deny_frozen_access(self);
      self.push(object);
      return self;
    });
    
    $def(self, '$<=>', function $Array_$lt_eq_gt$8(other) {
      var self = this;

      
      if ($eqeqeq($$$('Array'), other)) {
        other = other.$to_a();
      } else if ($truthy(other['$respond_to?']("to_ary"))) {
        other = other.$to_ary().$to_a();
      } else {
        return nil
      }      
      if (self.$hash() === other.$hash()) {
        return 0;
      }

      var count = Math.min(self.length, other.length);

      for (var i = 0; i < count; i++) {
        var tmp = (self[i])['$<=>'](other[i]);

        if (tmp !== 0) {
          return tmp;
        }
      }

      return (self.length)['$<=>'](other.length);
    });
    
    $def(self, '$==', function $Array_$eq_eq$9(other) {
      var self = this;

      
      var recursed = {};

      function _eqeq(array, other) {
        var i, length, a, b;

        if (array === other)
          return true;

        if (!other.$$is_array) {
          if ($respond_to(other, '$to_ary')) {
            return (other)['$=='](array);
          } else {
            return false;
          }
        }

        if (array.$$constructor !== Array)
          array = (array).$to_a();
        if (other.$$constructor !== Array)
          other = (other).$to_a();

        if (array.length !== other.length) {
          return false;
        }

        recursed[(array).$object_id()] = true;

        for (i = 0, length = array.length; i < length; i++) {
          a = array[i];
          b = other[i];
          if (a.$$is_array) {
            if (b.$$is_array && b.length !== a.length) {
              return false;
            }
            if (!recursed.hasOwnProperty((a).$object_id())) {
              if (!_eqeq(a, b)) {
                return false;
              }
            }
          } else {
            if (!(a)['$=='](b)) {
              return false;
            }
          }
        }

        return true;
      }

      return _eqeq(self, other);
    
    });
    
    function $array_slice_range(self, index) {
      var size = self.length,
          exclude, from, to, result;

      exclude = index.excl;
      from    = index.begin === nil ? 0 : $coerce_to(index.begin, Opal.Integer, 'to_int');
      to      = index.end === nil ? -1 : $coerce_to(index.end, Opal.Integer, 'to_int');

      if (from < 0) {
        from += size;

        if (from < 0) {
          return nil;
        }
      }

      if (index.excl_rev && index.begin !== nil) {
        from += 1;
      }

      if (from > size) {
        return nil;
      }

      if (to < 0) {
        to += size;

        if (to < 0) {
          return [];
        }
      }

      if (!exclude || index.end === nil) {
        to += 1;
      }

      result = self.slice(from, to);
      return result;
    }

    function $array_slice_arithmetic_seq(self, index) {
      var array, out = [], i = 0, pseudorange;

      if (index.step < 0) {
        pseudorange = {
          begin: index.range.end,
          end: index.range.begin,
          excl: false,
          excl_rev: index.range.excl
        };
        array = $array_slice_range(self, pseudorange).$reverse();
      }
      else {
        array = $array_slice_range(self, index.range);
      }

      while (i < array.length) {
        out.push(array[i]);
        i += Math.abs(index.step);
      }

      return out;
    }

    function $array_slice_index_length(self, index, length) {
      var size = self.length,
          result;

      index = $coerce_to(index, Opal.Integer, 'to_int');

      if (index < 0) {
        index += size;

        if (index < 0) {
          return nil;
        }
      }

      if (length === undefined) {
        if (index >= size || index < 0) {
          return nil;
        }

        return self[index];
      }
      else {
        length = $coerce_to(length, Opal.Integer, 'to_int');

        if (length < 0 || index > size || index < 0) {
          return nil;
        }

        result = self.slice(index, index + length);
      }
      return result;
    }
    
    $def(self, '$[]', function $Array_$$$10(index, length) {
      var self = this;
      
      if (index.$$is_range) {
        return $array_slice_range(self, index);
      }
      else if (index.$$is_arithmetic_seq) {
        return $array_slice_arithmetic_seq(self, index);
      }
      else {
        return $array_slice_index_length(self, index, length);
      }
    }, -2);
    
    $def(self, '$[]=', function $Array_$$$eq$11(index, value, extra) {
      var self = this, data = nil, length = nil;
      $deny_frozen_access(self);
      data = nil;
      
      var i, size = self.length;

      if (index.$$is_range) {
        if (value.$$is_array)
          data = value.$to_a();
        else if (value['$respond_to?']("to_ary"))
          data = value.$to_ary().$to_a();
        else
          data = [value];

        var exclude = index.excl,
            from    = index.begin === nil ? 0 : $coerce_to(index.begin, Opal.Integer, 'to_int'),
            to      = index.end === nil ? -1 : $coerce_to(index.end, Opal.Integer, 'to_int');

        if (from < 0) {
          from += size;

          if (from < 0) {
            $Kernel.$raise($$$('RangeError'), "" + (index.$inspect()) + " out of range");
          }
        }

        if (to < 0) {
          to += size;
        }

        if (!exclude || index.end === nil) {
          to += 1;
        }

        if (from > size) {
          for (i = size; i < from; i++) {
            self[i] = nil;
          }
        }

        if (to < 0) {
          self.splice.apply(self, [from, 0].concat(data));
        }
        else {
          self.splice.apply(self, [from, to - from].concat(data));
        }

        return value;
      } else {
        if (extra === undefined) {
          (length = 1);
        } else {
          length = value;
          value  = extra;

          if (value.$$is_array)
            data = value.$to_a();
          else if (value['$respond_to?']("to_ary"))
            data = value.$to_ary().$to_a();
          else
            data = [value];
        }

        var old;

        index  = $coerce_to(index, $$$('Integer'), 'to_int');
        length = $coerce_to(length, $$$('Integer'), 'to_int');

        if (index < 0) {
          old    = index;
          index += size;

          if (index < 0) {
            $Kernel.$raise($$$('IndexError'), "index " + (old) + " too small for array; minimum " + (-self.length));
          }
        }

        if (length < 0) {
          $Kernel.$raise($$$('IndexError'), "negative length (" + (length) + ")");
        }

        if (index > size) {
          for (i = size; i < index; i++) {
            self[i] = nil;
          }
        }

        if (extra === undefined) {
          self[index] = value;
        }
        else {
          self.splice.apply(self, [index, length].concat(data));
        }

        return value;
      }
    }, -3);
    
    $def(self, '$any?', function $Array_any$ques$12(pattern) {
      var block = $Array_any$ques$12.$$p || nil, self = this;

      $Array_any$ques$12.$$p = null;
      if (self.length === 0) return false;
      return $send2(self, $find_super(self, 'any?', $Array_any$ques$12, false, true), 'any?', [pattern], block);
    }, -1);
    
    $def(self, '$assoc', function $$assoc(object) {
      var self = this;

      
      for (var i = 0, length = self.length, item; i < length; i++) {
        if (item = self[i], item.length && (item[0])['$=='](object)) {
          return item;
        }
      }

      return nil;
    
    });
    
    $def(self, '$at', function $$at(index) {
      var self = this;

      
      index = $coerce_to(index, $$$('Integer'), 'to_int');

      if (index < 0) {
        index += self.length;
      }

      if (index < 0 || index >= self.length) {
        return nil;
      }

      return self[index];
    
    });
    
    $def(self, '$bsearch_index', function $$bsearch_index() {
      var block = $$bsearch_index.$$p || nil, self = this;

      $$bsearch_index.$$p = null;
      if (!(block !== nil)) {
        return self.$enum_for("bsearch_index")
      }      
      var min = 0,
          max = self.length,
          mid,
          val,
          ret,
          smaller = false,
          satisfied = nil;

      while (min < max) {
        mid = min + Math.floor((max - min) / 2);
        val = self[mid];
        ret = $yield1(block, val);

        if (ret === true) {
          satisfied = mid;
          smaller = true;
        }
        else if (ret === false || ret === nil) {
          smaller = false;
        }
        else if (ret.$$is_number) {
          if (ret === 0) { return mid; }
          smaller = (ret < 0);
        }
        else {
          $Kernel.$raise($$$('TypeError'), "wrong argument type " + ((ret).$class()) + " (must be numeric, true, false or nil)");
        }

        if (smaller) { max = mid; } else { min = mid + 1; }
      }

      return satisfied;
    });
    
    $def(self, '$bsearch', function $$bsearch() {
      var block = $$bsearch.$$p || nil, self = this, index = nil;

      $$bsearch.$$p = null;
      if (!(block !== nil)) {
        return self.$enum_for("bsearch")
      }      index = $send(self, 'bsearch_index', [], block.$to_proc());
      
      if (index != null && index.$$is_number) {
        return self[index];
      } else {
        return index;
      }
    });
    
    $def(self, '$cycle', function $$cycle(n) {
      var block = $$cycle.$$p || nil, self = this;

      $$cycle.$$p = null;
      if (n == null) n = nil;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["cycle", n], function $$13(){var self = $$13.$$s == null ? this : $$13.$$s;

          if ($truthy(n['$nil?']())) {
            return $$$($$$('Float'), 'INFINITY')
          } else {
            
            n = $Opal['$coerce_to!'](n, $$$('Integer'), "to_int");
            if ($truthy($rb_gt(n, 0))) {
              return $rb_times(self.$enumerator_size(), n)
            } else {
              return 0
            }          }}, {$$s: self})
      }      if (($truthy(self['$empty?']()) || ($eqeq(n, 0)))) {
        return nil
      }      
      var i, length;

      if (n === nil) {
        while (true) {
          for (i = 0, length = self.length; i < length; i++) {
            $yield1(block, self[i]);
          }
        }
      }
      else {
        n = $Opal['$coerce_to!'](n, $$$('Integer'), "to_int");
        if (n <= 0) {
          return self;
        }

        while (n > 0) {
          for (i = 0, length = self.length; i < length; i++) {
            $yield1(block, self[i]);
          }

          n--;
        }
      }
      return self;
    }, -1);
    
    $def(self, '$clear', function $$clear() {
      var self = this;

      
      $deny_frozen_access(self);
      self.splice(0, self.length);
      return self;
    });
    
    $def(self, '$count', function $$count(object) {
      var block = $$count.$$p || nil, self = this;

      $$count.$$p = null;
      if (($truthy(object !== undefined) || ($truthy(block)))) {
        return $send2(self, $find_super(self, 'count', $$count, false, true), 'count', [object], block)
      } else {
        return self.$size()
      }    }, -1);
    
    $def(self, '$initialize_copy', function $$initialize_copy(other) {
      var self = this;

      return self.$replace(other)
    });
    
    $def(self, '$collect', function $$collect() {
      var block = $$collect.$$p || nil, self = this;

      $$collect.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["collect"], function $$14(){var self = $$14.$$s == null ? this : $$14.$$s;

          return self.$size()}, {$$s: self})
      }      
      var result = [];

      for (var i = 0, length = self.length; i < length; i++) {
        var value = $yield1(block, self[i]);
        result.push(value);
      }

      return result;
    });
    
    $def(self, '$collect!', function $Array_collect$excl$15() {
      var block = $Array_collect$excl$15.$$p || nil, self = this;

      $Array_collect$excl$15.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["collect!"], function $$16(){var self = $$16.$$s == null ? this : $$16.$$s;

          return self.$size()}, {$$s: self})
      }      
      $deny_frozen_access(self);

      for (var i = 0, length = self.length; i < length; i++) {
        var value = $yield1(block, self[i]);
        self[i] = value;
      }
      return self;
    });
    
    function binomial_coefficient(n, k) {
      if (n === k || k === 0) {
        return 1;
      }

      if (k > 0 && n > k) {
        return binomial_coefficient(n - 1, k - 1) + binomial_coefficient(n - 1, k);
      }

      return 0;
    }
    
    $def(self, '$combination', function $$combination(n) {
      var $yield = $$combination.$$p || nil, self = this, num = nil;

      $$combination.$$p = null;
      
      num = $Opal['$coerce_to!'](n, $$$('Integer'), "to_int");
      if (!($yield !== nil)) {
        return $send(self, 'enum_for', ["combination", num], function $$17(){var self = $$17.$$s == null ? this : $$17.$$s;

          return binomial_coefficient(self.length, num)}, {$$s: self})
      }      
      var i, length, stack, chosen, lev, done, next;

      if (num === 0) {
        Opal.yield1($yield, []);
      } else if (num === 1) {
        for (i = 0, length = self.length; i < length; i++) {
          Opal.yield1($yield, [self[i]]);
        }
      }
      else if (num === self.length) {
        Opal.yield1($yield, self.slice());
      }
      else if (num >= 0 && num < self.length) {
        stack = [];
        for (i = 0; i <= num + 1; i++) {
          stack.push(0);
        }

        chosen = [];
        lev = 0;
        done = false;
        stack[0] = -1;

        while (!done) {
          chosen[lev] = self[stack[lev+1]];
          while (lev < num - 1) {
            lev++;
            next = stack[lev+1] = stack[lev] + 1;
            chosen[lev] = self[next];
          }
          Opal.yield1($yield, chosen.slice());
          lev++;
          do {
            done = (lev === 0);
            stack[lev]++;
            lev--;
          } while ( stack[lev+1] + num === self.length + lev + 1 );
        }
      }
      return self;
    });
    
    $def(self, '$repeated_combination', function $$repeated_combination(n) {
      var $yield = $$repeated_combination.$$p || nil, self = this, num = nil;

      $$repeated_combination.$$p = null;
      
      num = $Opal['$coerce_to!'](n, $$$('Integer'), "to_int");
      if (!($yield !== nil)) {
        return $send(self, 'enum_for', ["repeated_combination", num], function $$18(){var self = $$18.$$s == null ? this : $$18.$$s;

          return binomial_coefficient(self.length + num - 1, num);}, {$$s: self})
      }      
      function iterate(max, from, buffer, self) {
        if (buffer.length == max) {
          var copy = buffer.slice();
          Opal.yield1($yield, copy);
          return;
        }
        for (var i = from; i < self.length; i++) {
          buffer.push(self[i]);
          iterate(max, i, buffer, self);
          buffer.pop();
        }
      }

      if (num >= 0) {
        iterate(num, 0, [], self);
      }
      return self;
    });
    
    $def(self, '$compact', function $$compact() {
      var self = this;

      
      var result = [];

      for (var i = 0, length = self.length, item; i < length; i++) {
        if ((item = self[i]) !== nil) {
          result.push(item);
        }
      }

      return result;
    
    });
    
    $def(self, '$compact!', function $Array_compact$excl$19() {
      var self = this;

      
      $deny_frozen_access(self);

      var original = self.length;

      for (var i = 0, length = self.length; i < length; i++) {
        if (self[i] === nil) {
          self.splice(i, 1);

          length--;
          i--;
        }
      }

      return self.length === original ? nil : self;
    
    });
    
    $def(self, '$concat', function $$concat($a) {
      var $post_args, others, self = this;

      
      $post_args = $slice(arguments);
      others = $post_args;
      $deny_frozen_access(self);
      others = $send(others, 'map', [], function $$20(other){var self = $$20.$$s == null ? this : $$20.$$s;

        
        if (other == null) other = nil;
        other = ($eqeqeq($$$('Array'), other) ? (other.$to_a()) : (($coerce_to(other, $$$('Array'), 'to_ary')).$to_a()));
        if ($truthy(other['$equal?'](self))) {
          other = other.$dup();
        }        return other;}, {$$s: self});
      $send(others, 'each', [], function $$21(other){var self = $$21.$$s == null ? this : $$21.$$s;

        
        if (other == null) other = nil;
        
        for (var i = 0, length = other.length; i < length; i++) {
          self.push(other[i]);
        }
}, {$$s: self});
      return self;
    }, -1);
    
    $def(self, '$delete', function $Array_delete$22(object) {
      var $yield = $Array_delete$22.$$p || nil, self = this;

      $Array_delete$22.$$p = null;
      
      var original = self.length;

      for (var i = 0, length = original; i < length; i++) {
        if ((self[i])['$=='](object)) {
          $deny_frozen_access(self);

          self.splice(i, 1);

          length--;
          i--;
        }
      }

      if (self.length === original) {
        if (($yield !== nil)) {
          return Opal.yieldX($yield, []);
        }
        return nil;
      }
      return object;
    
    });
    
    $def(self, '$delete_at', function $$delete_at(index) {
      var self = this;

      
      $deny_frozen_access(self);

      index = $coerce_to(index, $$$('Integer'), 'to_int');

      if (index < 0) {
        index += self.length;
      }

      if (index < 0 || index >= self.length) {
        return nil;
      }

      var result = self[index];

      self.splice(index, 1);

      return result;
    
    });
    
    $def(self, '$delete_if', function $$delete_if() {
      var block = $$delete_if.$$p || nil, self = this;

      $$delete_if.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["delete_if"], function $$23(){var self = $$23.$$s == null ? this : $$23.$$s;

          return self.$size()}, {$$s: self})
      }      
      $deny_frozen_access(self);

      filterIf(self, $falsy, block)
    ;
      return self;
    });
    
    $def(self, '$difference', function $$difference($a) {
      var $post_args, arrays, self = this;

      
      $post_args = $slice(arguments);
      arrays = $post_args;
      return $send(arrays, 'reduce', [self.$to_a().$dup()], function $$24(a, b){
        
        if (a == null) a = nil;
        if (b == null) b = nil;
        return $rb_minus(a, b);});
    }, -1);
    
    $def(self, '$dig', function $$dig(idx, $a) {
      var $post_args, idxs, self = this, item = nil;

      
      $post_args = $slice(arguments, 1);
      idxs = $post_args;
      item = self['$[]'](idx);
      
      if (item === nil || idxs.length === 0) {
        return item;
      }
      if (!$truthy(item['$respond_to?']("dig"))) {
        $Kernel.$raise($$$('TypeError'), "" + (item.$class()) + " does not have #dig method");
      }      return $send(item, 'dig', $to_a(idxs));
    }, -2);
    
    $def(self, '$drop', function $$drop(number) {
      var self = this;

      
      number = $coerce_to(number, $$$('Integer'), 'to_int');

      if (number < 0) {
        $Kernel.$raise($$$('ArgumentError'));
      }

      return self.slice(number);
    
    });
    
    $def(self, '$dup', function $$dup() {
      var $yield = $$dup.$$p || nil, self = this;

      $$dup.$$p = null;
      
      
      if (self.$$class === Opal.Array &&
          self.$$class.$allocate.$$pristine &&
          self.$copy_instance_variables.$$pristine &&
          self.$initialize_dup.$$pristine) {
        return self.slice(0);
      }
      return $send2(self, $find_super(self, 'dup', $$dup, false, true), 'dup', [], $yield);
    });
    
    $def(self, '$each', function $$each() {
      var block = $$each.$$p || nil, self = this;

      $$each.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["each"], function $$25(){var self = $$25.$$s == null ? this : $$25.$$s;

          return self.$size()}, {$$s: self})
      }      
      for (var i = 0, length = self.length; i < length; i++) {
        $yield1(block, self[i]);
      }
      return self;
    });
    
    $def(self, '$each_index', function $$each_index() {
      var block = $$each_index.$$p || nil, self = this;

      $$each_index.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["each_index"], function $$26(){var self = $$26.$$s == null ? this : $$26.$$s;

          return self.$size()}, {$$s: self})
      }      
      for (var i = 0, length = self.length; i < length; i++) {
        $yield1(block, i);
      }
      return self;
    });
    
    $def(self, '$empty?', function $Array_empty$ques$27() {
      var self = this;

      return self.length === 0;
    });
    
    $def(self, '$eql?', function $Array_eql$ques$28(other) {
      var self = this;

      
      var recursed = {};

      function _eql(array, other) {
        var i, length, a, b;

        if (!other.$$is_array) {
          return false;
        }

        other = other.$to_a();

        if (array.length !== other.length) {
          return false;
        }

        recursed[(array).$object_id()] = true;

        for (i = 0, length = array.length; i < length; i++) {
          a = array[i];
          b = other[i];
          if (a.$$is_array) {
            if (b.$$is_array && b.length !== a.length) {
              return false;
            }
            if (!recursed.hasOwnProperty((a).$object_id())) {
              if (!_eql(a, b)) {
                return false;
              }
            }
          } else {
            if (!(a)['$eql?'](b)) {
              return false;
            }
          }
        }

        return true;
      }

      return _eql(self, other);
    
    });
    
    $def(self, '$fetch', function $$fetch(index, defaults) {
      var block = $$fetch.$$p || nil, self = this;

      $$fetch.$$p = null;
      
      var original = index;

      index = $coerce_to(index, $$$('Integer'), 'to_int');

      if (index < 0) {
        index += self.length;
      }

      if (index >= 0 && index < self.length) {
        return self[index];
      }

      if (block !== nil && defaults != null) {
        self.$warn("warning: block supersedes default value argument");
      }

      if (block !== nil) {
        return block(original);
      }

      if (defaults != null) {
        return defaults;
      }

      if (self.length === 0) {
        $Kernel.$raise($$$('IndexError'), "index " + (original) + " outside of array bounds: 0...0");
      }
      else {
        $Kernel.$raise($$$('IndexError'), "index " + (original) + " outside of array bounds: -" + (self.length) + "..." + (self.length));
      }
    }, -2);
    
    $def(self, '$fill', function $$fill($a) {
      var block = $$fill.$$p || nil, $post_args, args, $b, $c, self = this, one = nil, two = nil, obj = nil, left = nil, right = nil;

      $$fill.$$p = null;
      $post_args = $slice(arguments);
      args = $post_args;
      
      $deny_frozen_access(self);

      var i, value;
      if ($truthy(block)) {
        
        if ($truthy(args.length > 2)) {
          $Kernel.$raise($$$('ArgumentError'), "wrong number of arguments (" + (args.$length()) + " for 0..2)");
        }        $c = args, $b = $to_ary($c), (one = ($b[0] == null ? nil : $b[0])), (two = ($b[1] == null ? nil : $b[1]));
      } else {
        
        if ($truthy(args.length == 0)) {
          $Kernel.$raise($$$('ArgumentError'), "wrong number of arguments (0 for 1..3)");
        } else if ($truthy(args.length > 3)) {
          $Kernel.$raise($$$('ArgumentError'), "wrong number of arguments (" + (args.$length()) + " for 1..3)");
        }        $c = args, $b = $to_ary($c), (obj = ($b[0] == null ? nil : $b[0])), (one = ($b[1] == null ? nil : $b[1])), (two = ($b[2] == null ? nil : $b[2]));
      }      if ($eqeqeq($$$('Range'), one)) {
        
        if ($truthy(two)) {
          $Kernel.$raise($$$('TypeError'), "length invalid with range");
        }        left = one.begin === nil ? 0 : $coerce_to(one.begin, $$$('Integer'), 'to_int');
        if ($truthy(left < 0)) {
          left += this.length;
        }        if ($truthy(left < 0)) {
          $Kernel.$raise($$$('RangeError'), "" + (one.$inspect()) + " out of range");
        }        right = one.end === nil ? -1 : $coerce_to(one.end, $$$('Integer'), 'to_int');
        if ($truthy(right < 0)) {
          right += this.length;
        }        if (!$truthy(one['$exclude_end?']())) {
          right += 1;
        }        if ($truthy(right <= left)) {
          return self
        }      } else if ($truthy(one)) {
        
        left = $coerce_to(one, $$$('Integer'), 'to_int');
        if ($truthy(left < 0)) {
          left += this.length;
        }        if ($truthy(left < 0)) {
          left = 0;
        }        if ($truthy(two)) {
          
          right = $coerce_to(two, $$$('Integer'), 'to_int');
          if ($truthy(right == 0)) {
            return self
          }          right += left;
        } else {
          right = this.length;
        }      } else {
        
        left = 0;
        right = this.length;
      }      if ($truthy(left > this.length)) {
        
        for (i = this.length; i < right; i++) {
          self[i] = nil;
        }
      
      }      if ($truthy(right > this.length)) {
        this.length = right;
      }      if ($truthy(block)) {
        
        for (this.length; left < right; left++) {
          value = block(left);
          self[left] = value;
        }
      
      } else {
        
        for (this.length; left < right; left++) {
          self[left] = obj;
        }
      
      }      return self;
    }, -1);
    
    $def(self, '$first', function $$first(count) {
      var self = this;
      
      if (count == null) {
        return self.length === 0 ? nil : self[0];
      }

      count = $coerce_to(count, $$$('Integer'), 'to_int');

      if (count < 0) {
        $Kernel.$raise($$$('ArgumentError'), "negative array size");
      }

      return self.slice(0, count);
    }, -1);
    
    $def(self, '$flatten', function $$flatten(level) {
      var self = this;
      
      function _flatten(array, level) {
        var result = [],
            i, length,
            item, ary;

        array = (array).$to_a();

        for (i = 0, length = array.length; i < length; i++) {
          item = array[i];

          if (!$respond_to(item, '$to_ary', true)) {
            result.push(item);
            continue;
          }

          ary = (item).$to_ary();

          if (ary === nil) {
            result.push(item);
            continue;
          }

          if (!ary.$$is_array) {
            $Kernel.$raise($$$('TypeError'));
          }

          if (ary === self) {
            $Kernel.$raise($$$('ArgumentError'));
          }

          switch (level) {
          case undefined:
            result = result.concat(_flatten(ary));
            break;
          case 0:
            result.push(ary);
            break;
          default:
            result.push.apply(result, _flatten(ary, level - 1));
          }
        }
        return result;
      }

      if (level !== undefined) {
        level = $coerce_to(level, $$$('Integer'), 'to_int');
      }

      return _flatten(self, level);
    }, -1);
    
    $def(self, '$flatten!', function $Array_flatten$excl$29(level) {
      var self = this;
      
      $deny_frozen_access(self);

      var flattened = self.$flatten(level);

      if (self.length == flattened.length) {
        for (var i = 0, length = self.length; i < length; i++) {
          if (self[i] !== flattened[i]) {
            break;
          }
        }

        if (i == length) {
          return nil;
        }
      }

      self.$replace(flattened);
      return self;
    }, -1);
    
    $def(self, '$freeze', function $$freeze() {
      var self = this;

      
      if ($truthy(self['$frozen?']())) {
        return self
      }      return $freeze(self);    });
    
    $def(self, '$hash', function $$hash() {
      var self = this;

      
      var top = ($hash_ids === undefined),
          result = ['A'],
          hash_id = self.$object_id(),
          item, i, key;

      try {
        if (top) {
          $hash_ids = Object.create(null);
        }

        // return early for recursive structures
        if ($hash_ids[hash_id]) {
          return 'self';
        }

        for (key in $hash_ids) {
          item = $hash_ids[key];
          if (self['$eql?'](item)) {
            return 'self';
          }
        }

        $hash_ids[hash_id] = self;

        for (i = 0; i < self.length; i++) {
          item = self[i];
          result.push(item.$hash());
        }

        return result.join(',');
      } finally {
        if (top) {
          $hash_ids = undefined;
        }
      }
    
    });
    
    $def(self, '$include?', function $Array_include$ques$30(member) {
      var self = this;

      
      for (var i = 0, length = self.length; i < length; i++) {
        if ((self[i])['$=='](member)) {
          return true;
        }
      }

      return false;
    
    });
    
    $def(self, '$index', function $$index(object) {
      var block = $$index.$$p || nil, self = this;

      $$index.$$p = null;
      
      var i, length, value;

      if (object != null && block !== nil) {
        self.$warn("warning: given block not used");
      }

      if (object != null) {
        for (i = 0, length = self.length; i < length; i++) {
          if ((self[i])['$=='](object)) {
            return i;
          }
        }
      }
      else if (block !== nil) {
        for (i = 0, length = self.length; i < length; i++) {
          value = block(self[i]);

          if (value !== false && value !== nil) {
            return i;
          }
        }
      }
      else {
        return self.$enum_for("index");
      }

      return nil;
    }, -1);
    
    $def(self, '$insert', function $$insert(index, $a) {
      var $post_args, objects, self = this;

      
      $post_args = $slice(arguments, 1);
      objects = $post_args;
      
      $deny_frozen_access(self);

      index = $coerce_to(index, $$$('Integer'), 'to_int');

      if (objects.length > 0) {
        if (index < 0) {
          index += self.length + 1;

          if (index < 0) {
            $Kernel.$raise($$$('IndexError'), "" + (index) + " is out of bounds");
          }
        }
        if (index > self.length) {
          for (var i = self.length; i < index; i++) {
            self.push(nil);
          }
        }

        self.splice.apply(self, [index, 0].concat(objects));
      }
      return self;
    }, -2);
    var inspect_stack = [];
    
    $def(self, '$inspect', function $$inspect() {
      var self = this;

      
      
      var result = [],
      id = self.$__id__(),
      pushed = true;
      
      return (function() { try {
      
      
        if (inspect_stack.indexOf(id) !== -1) {
          pushed = false;
          return '[...]';
        }
        inspect_stack.push(id);

        for (var i = 0, length = self.length; i < length; i++) {
          var item = self['$[]'](i);

          result.push($$('Opal').$inspect(item));
        }

        return '[' + result.join(', ') + ']';
      ;
      return nil;
      } finally {
        if (pushed) inspect_stack.pop();
      } })();    });
    
    $def(self, '$intersection', function $$intersection($a) {
      var $post_args, arrays, self = this;

      
      $post_args = $slice(arguments);
      arrays = $post_args;
      return $send(arrays, 'reduce', [self.$to_a().$dup()], function $$31(a, b){
        
        if (a == null) a = nil;
        if (b == null) b = nil;
        return a['$&'](b);});
    }, -1);
    
    $def(self, '$intersect?', function $Array_intersect$ques$32(other) {
      var self = this;

      return self.$intersection(other)['$empty?']()['$!']()
    });
    
    $def(self, '$join', function $$join(sep) {
      var self = this;
      if ($gvars[","] == null) $gvars[","] = nil;

      
      if (sep == null) sep = nil;
      if ($truthy(self.length === 0)) {
        return ""
      }      if ($truthy(sep === nil)) {
        sep = $gvars[","];
      }      
      var result = [];
      var i, length, item, tmp;

      for (i = 0, length = self.length; i < length; i++) {
        item = self[i];

        if ($respond_to(item, '$to_str')) {
          tmp = (item).$to_str();

          if (tmp !== nil) {
            result.push((tmp).$to_s());

            continue;
          }
        }

        if ($respond_to(item, '$to_ary')) {
          tmp = (item).$to_ary();

          if (tmp === self) {
            $Kernel.$raise($$$('ArgumentError'));
          }

          if (tmp !== nil) {
            result.push((tmp).$join(sep));

            continue;
          }
        }

        if ($respond_to(item, '$to_s')) {
          tmp = (item).$to_s();

          if (tmp !== nil) {
            result.push(tmp);

            continue;
          }
        }

        $Kernel.$raise($$$('NoMethodError').$new("" + ($$('Opal').$inspect(self.$item())) + " doesn't respond to #to_str, #to_ary or #to_s", "to_str"));
      }

      if (sep === nil) {
        return result.join('');
      }
      else {
        return result.join($Opal['$coerce_to!'](sep, $$$('String'), "to_str").$to_s());
      }
    }, -1);
    
    $def(self, '$keep_if', function $$keep_if() {
      var block = $$keep_if.$$p || nil, self = this;

      $$keep_if.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["keep_if"], function $$33(){var self = $$33.$$s == null ? this : $$33.$$s;

          return self.$size()}, {$$s: self})
      }      
      $deny_frozen_access(self);

      filterIf(self, $truthy, block)
    ;
      return self;
    });
    
    $def(self, '$last', function $$last(count) {
      var self = this;
      
      if (count == null) {
        return self.length === 0 ? nil : self[self.length - 1];
      }

      count = $coerce_to(count, $$$('Integer'), 'to_int');

      if (count < 0) {
        $Kernel.$raise($$$('ArgumentError'), "negative array size");
      }

      if (count > self.length) {
        count = self.length;
      }

      return self.slice(self.length - count, self.length);
    }, -1);
    
    $def(self, '$length', function $$length() {
      var self = this;

      return self.length;
    });
    
    $def(self, '$max', function $$max(n) {
      var block = $$max.$$p || nil, self = this;

      $$max.$$p = null;
      return $send(self.$each(), 'max', [n], block.$to_proc());
    }, -1);
    
    $def(self, '$min', function $$min() {
      var block = $$min.$$p || nil, self = this;

      $$min.$$p = null;
      return $send(self.$each(), 'min', [], block.$to_proc());
    });
    
    // Returns the product of from, from-1, ..., from - how_many + 1.
    function descending_factorial(from, how_many) {
      var count = how_many >= 0 ? 1 : 0;
      while (how_many) {
        count *= from;
        from--;
        how_many--;
      }
      return count;
    }
    
    $def(self, '$permutation', function $$permutation(num) {
      var block = $$permutation.$$p || nil, self = this, perm = nil, used = nil;

      $$permutation.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["permutation", num], function $$34(){var self = $$34.$$s == null ? this : $$34.$$s;

          return descending_factorial(self.length, num === undefined ? self.length : num);}, {$$s: self})
      }      
      var permute, offensive, output;

      if (num === undefined) {
        num = self.length;
      }
      else {
        num = $coerce_to(num, $$$('Integer'), 'to_int');
      }

      if (num < 0 || self.length < num) ;
      else if (num === 0) {
        // exactly one permutation: the zero-length array
        Opal.yield1(block, []);
      }
      else if (num === 1) {
        // this is a special, easy case
        for (var i = 0; i < self.length; i++) {
          Opal.yield1(block, [self[i]]);
        }
      }
      else {
        // this is the general case
        (perm = $$('Array').$new(num));
        (used = $$('Array').$new(self.length, false));

        permute = function(num, perm, index, used, blk) {
          self = this;
          for(var i = 0; i < self.length; i++){
            if(used['$[]'](i)['$!']()) {
              perm[index] = i;
              if(index < num - 1) {
                used[i] = true;
                permute.call(self, num, perm, index + 1, used, blk);
                used[i] = false;
              }
              else {
                output = [];
                for (var j = 0; j < perm.length; j++) {
                  output.push(self[perm[j]]);
                }
                $yield1(blk, output);
              }
            }
          }
        };

        if ((block !== nil)) {
          // offensive (both definitions) copy.
          offensive = self.slice();
          permute.call(offensive, num, perm, 0, used, block);
        }
        else {
          permute.call(self, num, perm, 0, used, block);
        }
      }
      return self;
    }, -1);
    
    $def(self, '$repeated_permutation', function $$repeated_permutation(n) {
      var $yield = $$repeated_permutation.$$p || nil, self = this, num = nil;

      $$repeated_permutation.$$p = null;
      
      num = $Opal['$coerce_to!'](n, $$$('Integer'), "to_int");
      if (!($yield !== nil)) {
        return $send(self, 'enum_for', ["repeated_permutation", num], function $$35(){var self = $$35.$$s == null ? this : $$35.$$s;

          if ($truthy($rb_ge(num, 0))) {
            return self.$size()['$**'](num)
          } else {
            return 0
          }}, {$$s: self})
      }      
      function iterate(max, buffer, self) {
        if (buffer.length == max) {
          var copy = buffer.slice();
          Opal.yield1($yield, copy);
          return;
        }
        for (var i = 0; i < self.length; i++) {
          buffer.push(self[i]);
          iterate(max, buffer, self);
          buffer.pop();
        }
      }

      iterate(num, [], self.slice());
      return self;
    });
    
    $def(self, '$pop', function $$pop(count) {
      var self = this;
      $deny_frozen_access(self);
      if ($truthy(count === undefined)) {
        
        if ($truthy(self.length === 0)) {
          return nil
        }        return self.pop();
      }      count = $coerce_to(count, $$$('Integer'), 'to_int');
      if ($truthy(count < 0)) {
        $Kernel.$raise($$$('ArgumentError'), "negative array size");
      }      if ($truthy(self.length === 0)) {
        return []
      }      if ($truthy(count === 1)) {
        return [self.pop()];
      } else if ($truthy(count > self.length)) {
        return self.splice(0, self.length);
      } else {
        return self.splice(self.length - count, self.length);
      }    }, -1);
    
    $def(self, '$product', function $$product($a) {
      var block = $$product.$$p || nil, $post_args, args, self = this;

      $$product.$$p = null;
      $post_args = $slice(arguments);
      args = $post_args;
      
      var result = (block !== nil) ? null : [],
          n = args.length + 1,
          counters = new Array(n),
          lengths  = new Array(n),
          arrays   = new Array(n),
          i, m, subarray, len, resultlen = 1;

      arrays[0] = self;
      for (i = 1; i < n; i++) {
        arrays[i] = $coerce_to(args[i - 1], $$$('Array'), 'to_ary');
      }

      for (i = 0; i < n; i++) {
        len = arrays[i].length;
        if (len === 0) {
          return result || self;
        }
        resultlen *= len;
        if (resultlen > 2147483647) {
          $Kernel.$raise($$$('RangeError'), "too big to product");
        }
        lengths[i] = len;
        counters[i] = 0;
      }

      outer_loop: for (;;) {
        subarray = [];
        for (i = 0; i < n; i++) {
          subarray.push(arrays[i][counters[i]]);
        }
        if (result) {
          result.push(subarray);
        } else {
          Opal.yield1(block, subarray);
        }
        m = n - 1;
        counters[m]++;
        while (counters[m] === lengths[m]) {
          counters[m] = 0;
          if (--m < 0) break outer_loop;
          counters[m]++;
        }
      }

      return result || self;
    }, -1);
    
    $def(self, '$push', function $$push($a) {
      var $post_args, objects, self = this;

      
      $post_args = $slice(arguments);
      objects = $post_args;
      
      $deny_frozen_access(self);

      for (var i = 0, length = objects.length; i < length; i++) {
        self.push(objects[i]);
      }
      return self;
    }, -1);
    
    $def(self, '$rassoc', function $$rassoc(object) {
      var self = this;

      
      for (var i = 0, length = self.length, item; i < length; i++) {
        item = self[i];

        if (item.length && item[1] !== undefined) {
          if ((item[1])['$=='](object)) {
            return item;
          }
        }
      }

      return nil;
    
    });
    
    $def(self, '$reject', function $$reject() {
      var block = $$reject.$$p || nil, self = this;

      $$reject.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["reject"], function $$36(){var self = $$36.$$s == null ? this : $$36.$$s;

          return self.$size()}, {$$s: self})
      }      
      var result = [];

      for (var i = 0, length = self.length, value; i < length; i++) {
        value = block(self[i]);

        if (value === false || value === nil) {
          result.push(self[i]);
        }
      }
      return result;
    });
    
    $def(self, '$reject!', function $Array_reject$excl$37() {
      var block = $Array_reject$excl$37.$$p || nil, self = this, original = nil;

      $Array_reject$excl$37.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["reject!"], function $$38(){var self = $$38.$$s == null ? this : $$38.$$s;

          return self.$size()}, {$$s: self})
      }      $deny_frozen_access(self);
      original = self.$length();
      $send(self, 'delete_if', [], block.$to_proc());
      if ($eqeq(self.$length(), original)) {
        return nil
      } else {
        return self
      }    });
    
    $def(self, '$replace', function $$replace(other) {
      var self = this;

      
      $deny_frozen_access(self);
      other = ($eqeqeq($$$('Array'), other) ? (other.$to_a()) : (($coerce_to(other, $$$('Array'), 'to_ary')).$to_a()));
      
      self.splice(0, self.length);
      self.push.apply(self, other);
      return self;
    });
    
    $def(self, '$reverse', function $$reverse() {
      var self = this;

      return self.slice(0).reverse();
    });
    
    $def(self, '$reverse!', function $Array_reverse$excl$39() {
      var self = this;

      
      $deny_frozen_access(self);
      return self.reverse();    });
    
    $def(self, '$reverse_each', function $$reverse_each() {
      var block = $$reverse_each.$$p || nil, self = this;

      $$reverse_each.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["reverse_each"], function $$40(){var self = $$40.$$s == null ? this : $$40.$$s;

          return self.$size()}, {$$s: self})
      }      $send(self.$reverse(), 'each', [], block.$to_proc());
      return self;
    });
    
    $def(self, '$rindex', function $$rindex(object) {
      var block = $$rindex.$$p || nil, self = this;

      $$rindex.$$p = null;
      
      var i, value;

      if (object != null && block !== nil) {
        self.$warn("warning: given block not used");
      }

      if (object != null) {
        for (i = self.length - 1; i >= 0; i--) {
          if (i >= self.length) {
            break;
          }
          if ((self[i])['$=='](object)) {
            return i;
          }
        }
      }
      else if (block !== nil) {
        for (i = self.length - 1; i >= 0; i--) {
          if (i >= self.length) {
            break;
          }

          value = block(self[i]);

          if (value !== false && value !== nil) {
            return i;
          }
        }
      }
      else if (object == null) {
        return self.$enum_for("rindex");
      }

      return nil;
    }, -1);
    
    $def(self, '$rotate', function $$rotate(n) {
      var self = this;

      
      if (n == null) n = 1;
      
      var ary, idx, firstPart, lastPart;

      n = $coerce_to(n, $$$('Integer'), 'to_int');

      if (self.length === 1) {
        return self.slice();
      }
      if (self.length === 0) {
        return [];
      }

      ary = self.slice();
      idx = n % ary.length;

      firstPart = ary.slice(idx);
      lastPart = ary.slice(0, idx);
      return firstPart.concat(lastPart);
    }, -1);
    
    $def(self, '$rotate!', function $Array_rotate$excl$41(cnt) {
      var self = this, ary = nil;

      
      if (cnt == null) cnt = 1;
      
      $deny_frozen_access(self);

      if (self.length === 0 || self.length === 1) {
        return self;
      }
      cnt = $coerce_to(cnt, $$$('Integer'), 'to_int');
      ary = self.$rotate(cnt);
      return self.$replace(ary);
    }, -1);
    (function($base, $super) {
      var self = $klass($base, $super, 'SampleRandom');

      var $proto = self.$$prototype;

      $proto.rng = nil;
      
      
      $def(self, '$initialize', $assign_ivar("rng"));
      return $def(self, '$rand', function $$rand(size) {
        var self = this, random = nil;

        
        random = $coerce_to(self.rng.$rand(size), $$$('Integer'), 'to_int');
        if ($truthy(random < 0)) {
          $Kernel.$raise($$$('RangeError'), "random value must be >= 0");
        }        if (!$truthy(random < size)) {
          $Kernel.$raise($$$('RangeError'), "random value must be less than Array size");
        }        return random;
      });
    })(self, null);
    
    $def(self, '$sample', function $$sample(count, options) {
      var self = this, o = nil, rng = nil;
      if ($truthy(count === undefined)) {
        return self.$at($Kernel.$rand(self.length))
      }      if ($truthy(options === undefined)) {
        if ($truthy((o = $Opal['$coerce_to?'](count, $$$('Hash'), "to_hash")))) {
          
          options = o;
          count = nil;
        } else {
          
          options = nil;
          count = $coerce_to(count, $$$('Integer'), 'to_int');
        }
      } else {
        
        count = $coerce_to(count, $$$('Integer'), 'to_int');
        options = $coerce_to(options, $$$('Hash'), 'to_hash');
      }      if (($truthy(count) && ($truthy(count < 0)))) {
        $Kernel.$raise($$$('ArgumentError'), "count must be greater than 0");
      }      if ($truthy(options)) {
        rng = options['$[]']("random");
      }      rng = (($truthy(rng) && ($truthy(rng['$respond_to?']("rand")))) ? ($$('SampleRandom').$new(rng)) : ($Kernel));
      if (!$truthy(count)) {
        return self[rng.$rand(self.length)]
      }      

      var abandon, spin, result, i, j, k, targetIndex, oldValue;

      if (count > self.length) {
        count = self.length;
      }

      switch (count) {
        case 0:
          return [];
        case 1:
          return [self[rng.$rand(self.length)]];
        case 2:
          i = rng.$rand(self.length);
          j = rng.$rand(self.length - 1);
          if (i <= j) {
            j++;
          }
          return [self[i], self[j]];
        default:
          if (self.length / count > 3) {
            abandon = false;
            spin = 0;

            result = $$('Array').$new(count);
            i = 1;

            result[0] = rng.$rand(self.length);
            while (i < count) {
              k = rng.$rand(self.length);
              j = 0;

              while (j < i) {
                while (k === result[j]) {
                  spin++;
                  if (spin > 100) {
                    abandon = true;
                    break;
                  }
                  k = rng.$rand(self.length);
                }
                if (abandon) { break; }

                j++;
              }

              if (abandon) { break; }

              result[i] = k;

              i++;
            }

            if (!abandon) {
              i = 0;
              while (i < count) {
                result[i] = self[result[i]];
                i++;
              }

              return result;
            }
          }

          result = self.slice();

          for (var c = 0; c < count; c++) {
            targetIndex = rng.$rand(self.length - c) + c;
            oldValue = result[c];
            result[c] = result[targetIndex];
            result[targetIndex] = oldValue;
          }

          return count === self.length ? result : (result)['$[]'](0, count);
      }
    }, -1);
    
    $def(self, '$select', function $$select() {
      var block = $$select.$$p || nil, self = this;

      $$select.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["select"], function $$42(){var self = $$42.$$s == null ? this : $$42.$$s;

          return self.$size()}, {$$s: self})
      }      
      var result = [];

      for (var i = 0, length = self.length, item, value; i < length; i++) {
        item = self[i];

        value = $yield1(block, item);

        if ($truthy(value)) {
          result.push(item);
        }
      }

      return result;
    });
    
    $def(self, '$select!', function $Array_select$excl$43() {
      var block = $Array_select$excl$43.$$p || nil, self = this;

      $Array_select$excl$43.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["select!"], function $$44(){var self = $$44.$$s == null ? this : $$44.$$s;

          return self.$size()}, {$$s: self})
      }      
      $deny_frozen_access(self);

      var original = self.length;
      $send(self, 'keep_if', [], block.$to_proc());
      return self.length === original ? nil : self;
    });
    
    $def(self, '$shift', function $$shift(count) {
      var self = this;
      $deny_frozen_access(self);
      if ($truthy(count === undefined)) {
        
        if ($truthy(self.length === 0)) {
          return nil
        }        return shiftNoArg(self);
      }      count = $coerce_to(count, $$$('Integer'), 'to_int');
      if ($truthy(count < 0)) {
        $Kernel.$raise($$$('ArgumentError'), "negative array size");
      }      if ($truthy(self.length === 0)) {
        return []
      }      return self.splice(0, count);    }, -1);
    
    $def(self, '$shuffle', function $$shuffle(rng) {
      var self = this;
      return self.$dup().$to_a()['$shuffle!'](rng);
    }, -1);
    
    $def(self, '$shuffle!', function $Array_shuffle$excl$45(rng) {
      var self = this;
      
      $deny_frozen_access(self);

      var randgen, i = self.length, j, tmp;

      if (rng !== undefined) {
        rng = $Opal['$coerce_to?'](rng, $$$('Hash'), "to_hash");

        if (rng !== nil) {
          rng = rng['$[]']("random");

          if (rng !== nil && rng['$respond_to?']("rand")) {
            randgen = rng;
          }
        }
      }

      while (i) {
        if (randgen) {
          j = randgen.$rand(i).$to_int();

          if (j < 0) {
            $Kernel.$raise($$$('RangeError'), "random number too small " + (j));
          }

          if (j >= i) {
            $Kernel.$raise($$$('RangeError'), "random number too big " + (j));
          }
        }
        else {
          j = self.$rand(i);
        }

        tmp = self[--i];
        self[i] = self[j];
        self[j] = tmp;
      }

      return self;
    }, -1);
    
    $def(self, '$slice!', function $Array_slice$excl$46(index, length) {
      var self = this, result = nil, range = nil, range_start = nil, range_end = nil, start = nil;
      $deny_frozen_access(self);
      result = nil;
      if ($truthy(length === undefined)) {
        if ($eqeqeq($$$('Range'), index)) {
          
          range = index;
          result = self['$[]'](range);
          range_start = range.begin === nil ? 0 : $coerce_to(range.begin, $$$('Integer'), 'to_int');
          range_end = range.end === nil ? -1 : $coerce_to(range.end, $$$('Integer'), 'to_int');
          
          if (range_start < 0) {
            range_start += self.length;
          }

          if (range_end < 0) {
            range_end += self.length;
          } else if (range_end >= self.length) {
            range_end = self.length - 1;
            if (range.excl) {
              range_end += 1;
            }
          }

          var range_length = range_end - range_start;
          if (range.excl && range.end !== nil) {
            range_end -= 1;
          } else {
            range_length += 1;
          }

          if (range_start < self.length && range_start >= 0 && range_end < self.length && range_end >= 0 && range_length > 0) {
            self.splice(range_start, range_length);
          }
        } else {
          
          start = $coerce_to(index, $$$('Integer'), 'to_int');
          
          if (start < 0) {
            start += self.length;
          }

          if (start < 0 || start >= self.length) {
            return nil;
          }

          result = self[start];

          if (start === 0) {
            self.shift();
          } else {
            self.splice(start, 1);
          }
        }
      } else {
        
        start = $coerce_to(index, $$$('Integer'), 'to_int');
        length = $coerce_to(length, $$$('Integer'), 'to_int');
        
        if (length < 0) {
          return nil;
        }

        result = self['$[]'](start, length);

        if (start < 0) {
          start += self.length;
        }

        if (start + length > self.length) {
          length = self.length - start;
        }

        if (start < self.length && start >= 0) {
          self.splice(start, length);
        }
      }      return result;
    }, -2);
    
    $def(self, '$sort', function $$sort() {
      var block = $$sort.$$p || nil, self = this;

      $$sort.$$p = null;
      if (!$truthy(self.length > 1)) {
        return self
      }      
      if (block === nil) {
        block = function(a, b) {
          return (a)['$<=>'](b);
        };
      }

      return self.slice().sort(function(x, y) {
        var ret = block(x, y);

        if (ret === nil) {
          $Kernel.$raise($$$('ArgumentError'), "comparison of " + ((x).$inspect()) + " with " + ((y).$inspect()) + " failed");
        }

        return $rb_gt(ret, 0) ? 1 : ($rb_lt(ret, 0) ? -1 : 0);
      });
    });
    
    $def(self, '$sort!', function $Array_sort$excl$47() {
      var block = $Array_sort$excl$47.$$p || nil, self = this;

      $Array_sort$excl$47.$$p = null;
      
      $deny_frozen_access(self);

      var result;

      if ((block !== nil)) {
        result = $send((self.slice()), 'sort', [], block.$to_proc());
      }
      else {
        result = (self.slice()).$sort();
      }

      self.length = 0;
      for(var i = 0, length = result.length; i < length; i++) {
        self.push(result[i]);
      }

      return self;
    });
    
    $def(self, '$sort_by!', function $Array_sort_by$excl$48() {
      var block = $Array_sort_by$excl$48.$$p || nil, self = this;

      $Array_sort_by$excl$48.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["sort_by!"], function $$49(){var self = $$49.$$s == null ? this : $$49.$$s;

          return self.$size()}, {$$s: self})
      }      $deny_frozen_access(self);
      return self.$replace($send(self, 'sort_by', [], block.$to_proc()));
    });
    
    $def(self, '$take', function $$take(count) {
      var self = this;

      
      if (count < 0) {
        $Kernel.$raise($$$('ArgumentError'));
      }

      return self.slice(0, count);
    
    });
    
    $def(self, '$take_while', function $$take_while() {
      var block = $$take_while.$$p || nil, self = this;

      $$take_while.$$p = null;
      
      var result = [];

      for (var i = 0, length = self.length, item, value; i < length; i++) {
        item = self[i];

        value = block(item);

        if (value === false || value === nil) {
          return result;
        }

        result.push(item);
      }

      return result;
    });
    
    $def(self, '$to_a', function $$to_a() {
      var self = this;

      
      if (self.$$class === Opal.Array) {
        return self;
      }
      else {
        return Opal.Array.$new(self);
      }
    
    });
    
    $def(self, '$to_ary', $return_self);
    
    $def(self, '$to_h', function $$to_h() {
      var block = $$to_h.$$p || nil, self = this, array = nil;

      $$to_h.$$p = null;
      array = self;
      if ((block !== nil)) {
        array = $send(array, 'map', [], block.$to_proc());
      }      
      var i, len = array.length, ary, key, val, hash = $hash2([], {});

      for (i = 0; i < len; i++) {
        ary = $Opal['$coerce_to?'](array[i], $$$('Array'), "to_ary");
        if (!ary.$$is_array) {
          $Kernel.$raise($$$('TypeError'), "wrong element type " + ((ary).$class()) + " at " + (i) + " (expected array)");
        }
        if (ary.length !== 2) {
          $Kernel.$raise($$$('ArgumentError'), "wrong array length at " + (i) + " (expected 2, was " + ((ary).$length()) + ")");
        }
        key = ary[0];
        val = ary[1];
        $hash_put(hash, key, val);
      }

      return hash;
    });
    
    $def(self, '$transpose', function $$transpose() {
      var self = this, result = nil, max = nil;

      
      if ($truthy(self['$empty?']())) {
        return []
      }      result = [];
      max = nil;
      $send(self, 'each', [], function $$50(row){var $ret_or_1 = nil;

        
        if (row == null) row = nil;
        row = ($eqeqeq($$$('Array'), row) ? (row.$to_a()) : (($coerce_to(row, $$$('Array'), 'to_ary')).$to_a()));
        max = ($truthy(($ret_or_1 = max)) ? ($ret_or_1) : (row.length));
        if ($neqeq(row.length, max)) {
          $Kernel.$raise($$$('IndexError'), "element size differs (" + (row.length) + " should be " + (max) + ")");
        }        return $send((row.length), 'times', [], function $$51(i){var $a, entry = nil;

          
          if (i == null) i = nil;
          entry = ($truthy(($ret_or_1 = result['$[]'](i))) ? ($ret_or_1) : (($a = [i, []], $send(result, '[]=', $a), $a[$a.length - 1])));
          return entry['$<<'](row.$at(i));});});
      return result;
    });
    
    $def(self, '$union', function $$union($a) {
      var $post_args, arrays, self = this;

      
      $post_args = $slice(arguments);
      arrays = $post_args;
      return $send(arrays, 'reduce', [self.$uniq()], function $$52(a, b){
        
        if (a == null) a = nil;
        if (b == null) b = nil;
        return a['$|'](b);});
    }, -1);
    
    $def(self, '$uniq', function $$uniq() {
      var block = $$uniq.$$p || nil, self = this;

      $$uniq.$$p = null;
      
      var hash = $hash2([], {}), i, length, item, key;

      if (block === nil) {
        for (i = 0, length = self.length; i < length; i++) {
          item = self[i];
          if ($hash_get(hash, item) === undefined) {
            $hash_put(hash, item, item);
          }
        }
      }
      else {
        for (i = 0, length = self.length; i < length; i++) {
          item = self[i];
          key = $yield1(block, item);
          if ($hash_get(hash, key) === undefined) {
            $hash_put(hash, key, item);
          }
        }
      }

      return (hash).$values();
    });
    
    $def(self, '$uniq!', function $Array_uniq$excl$53() {
      var block = $Array_uniq$excl$53.$$p || nil, self = this;

      $Array_uniq$excl$53.$$p = null;
      
      $deny_frozen_access(self);

      var original_length = self.length, hash = $hash2([], {}), i, length, item, key;

      for (i = 0, length = original_length; i < length; i++) {
        item = self[i];
        key = (block === nil ? item : $yield1(block, item));

        if ($hash_get(hash, key) === undefined) {
          $hash_put(hash, key, item);
          continue;
        }

        self.splice(i, 1);
        length--;
        i--;
      }

      return self.length === original_length ? nil : self;
    });
    
    $def(self, '$unshift', function $$unshift($a) {
      var $post_args, objects, self = this;

      
      $post_args = $slice(arguments);
      objects = $post_args;
      
      $deny_frozen_access(self);

      var selfLength = self.length;
      var objectsLength = objects.length;
      if (objectsLength == 0) return self;
      var index = selfLength - objectsLength;
      for (var i = 0; i < objectsLength; i++) {
        self.push(self[index + i]);
      }
      var len = selfLength - 1;
      while (len - objectsLength >= 0) {
        self[len] = self[len - objectsLength];
        len--;
      }
      for (var j = 0; j < objectsLength; j++) {
        self[j] = objects[j];
      }
      return self;
    }, -1);
    
    $def(self, '$values_at', function $$values_at($a) {
      var $post_args, args, self = this, out = nil;

      
      $post_args = $slice(arguments);
      args = $post_args;
      out = [];
      $send(args, 'each', [], function $$54(elem){var self = $$54.$$s == null ? this : $$54.$$s, finish = nil, start = nil, i = nil;

        
        if (elem == null) elem = nil;
        if ($truthy(elem['$is_a?']($$$('Range')))) {
          
          finish = elem.$end() === nil ? -1 : $coerce_to(elem.$end(), $$$('Integer'), 'to_int');
          start = elem.$begin() === nil ? 0 : $coerce_to(elem.$begin(), $$$('Integer'), 'to_int');
          
          if (start < 0) {
            start = start + self.length;
            return nil;
          }
          
          if (finish < 0) {
            finish = finish + self.length;
          }
          if (elem['$exclude_end?']() && elem.$end() !== nil) {
            finish--;
          }
          if (finish < start) {
            return nil;
          }
          return $send(start, 'upto', [finish], function $$55(i){var self = $$55.$$s == null ? this : $$55.$$s;

            
            if (i == null) i = nil;
            return out['$<<'](self.$at(i));}, {$$s: self});
        } else {
          
          i = $coerce_to(elem, $$$('Integer'), 'to_int');
          return out['$<<'](self.$at(i));
        }}, {$$s: self});
      return out;
    }, -1);
    
    $def(self, '$zip', function $$zip($a) {
      var block = $$zip.$$p || nil, $post_args, others, self = this, $ret_or_1 = nil;

      $$zip.$$p = null;
      $post_args = $slice(arguments);
      others = $post_args;
      
      var result = [], size = self.length, part, o, i, j, jj;

      for (j = 0, jj = others.length; j < jj; j++) {
        o = others[j];
        if (o.$$is_array) {
          continue;
        }
        if (o.$$is_range || o.$$is_enumerator) {
          others[j] = o.$take(size);
          continue;
        }
        others[j] = ($truthy(($ret_or_1 = $Opal['$coerce_to?'](o, $$$('Array'), "to_ary"))) ? ($ret_or_1) : ($Opal['$coerce_to!'](o, $$$('Enumerator'), "to_enum", "each"))).$to_a();
      }

      for (i = 0; i < size; i++) {
        part = [self[i]];

        for (j = 0, jj = others.length; j < jj; j++) {
          o = others[j][i];

          if (o == null) {
            o = nil;
          }

          part[j + 1] = o;
        }

        result[i] = part;
      }

      if (block !== nil) {
        for (i = 0; i < size; i++) {
          Opal.yield1(block, result[i]);
        }

        return nil;
      }

      return result;
    }, -1);
    $defs(self, '$inherited', function $$inherited(klass) {
      
      
      klass.$$prototype.$to_a = function() {
        return this.slice(0, this.length);
      };
    
    });
    
    $def(self, '$instance_variables', function $$instance_variables() {
      var $yield = $$instance_variables.$$p || nil, self = this;

      $$instance_variables.$$p = null;
      return $send($send2(self, $find_super(self, 'instance_variables', $$instance_variables, false, true), 'instance_variables', [], $yield), 'reject', [], function $$56(ivar){var $ret_or_1 = nil;

        
        if (ivar == null) ivar = nil;
        if ($truthy(($ret_or_1 = /^@\d+$/.test(ivar)))) {
          return $ret_or_1
        } else {
          return ivar['$==']("@length")
        }})
    });
    
    $def(self, '$pack', function $$pack($a) {

      
      $slice(arguments);
      return $Kernel.$raise("To use Array#pack, you must first require 'corelib/array/pack'.");
    }, -1);
    $alias(self, "append", "push");
    $alias(self, "filter", "select");
    $alias(self, "filter!", "select!");
    $alias(self, "map", "collect");
    $alias(self, "map!", "collect!");
    $alias(self, "prepend", "unshift");
    $alias(self, "size", "length");
    $alias(self, "slice", "[]");
    $alias(self, "to_s", "inspect");
    $Opal.$pristine(self.$singleton_class(), "allocate");
    return $Opal.$pristine(self, "copy_instance_variables", "initialize_dup");
  })('::', Array, $nesting);
};

Opal.modules["corelib/hash"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $yield1 = Opal.yield1, $hash = Opal.hash, $hash_init = Opal.hash_init, $hash_get = Opal.hash_get, $hash_put = Opal.hash_put, $hash_delete = Opal.hash_delete, $deny_frozen_access = Opal.deny_frozen_access, $freeze = Opal.freeze, $klass = Opal.klass, $slice = Opal.slice, $Opal = Opal.Opal, $Kernel = Opal.Kernel, $defs = Opal.defs, $def = Opal.def, $send = Opal.send, $rb_ge = Opal.rb_ge, $rb_gt = Opal.rb_gt, $hash2 = Opal.hash2, $truthy = Opal.truthy, $to_a = Opal.to_a, $return_self = Opal.return_self, $alias = Opal.alias, self = Opal.top, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('require,include,coerce_to?,[],merge!,allocate,raise,coerce_to!,each,fetch,>=,>,==,compare_by_identity,lambda?,abs,arity,enum_for,size,respond_to?,class,dig,except!,dup,delete,new,inspect,map,to_proc,flatten,frozen?,eql?,default,default_proc,default_proc=,default=,to_h,proc,clone,select,select!,has_key?,indexes,index,length,[]=,has_value?');
  
  self.$require("corelib/enumerable");
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Hash');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting);

    
    self.$include($$$('Enumerable'));
    self.$$prototype.$$is_hash = true;
    $defs(self, '$[]', function $Hash_$$$1($a) {
      var $post_args, argv, self = this;

      
      $post_args = $slice(arguments);
      argv = $post_args;
      
      var hash, argc = argv.length, i;

      if (argc === 1) {
        hash = $Opal['$coerce_to?'](argv['$[]'](0), $$$('Hash'), "to_hash");
        if (hash !== nil) {
          return self.$allocate()['$merge!'](hash);
        }

        argv = $Opal['$coerce_to?'](argv['$[]'](0), $$$('Array'), "to_ary");
        if (argv === nil) {
          $Kernel.$raise($$$('ArgumentError'), "odd number of arguments for Hash");
        }

        argc = argv.length;
        hash = self.$allocate();

        for (i = 0; i < argc; i++) {
          if (!argv[i].$$is_array) continue;
          switch(argv[i].length) {
          case 1:
            hash.$store(argv[i][0], nil);
            break;
          case 2:
            hash.$store(argv[i][0], argv[i][1]);
            break;
          default:
            $Kernel.$raise($$$('ArgumentError'), "invalid number of elements (" + (argv[i].length) + " for 1..2)");
          }
        }

        return hash;
      }

      if (argc % 2 !== 0) {
        $Kernel.$raise($$$('ArgumentError'), "odd number of arguments for Hash");
      }

      hash = self.$allocate();

      for (i = 0; i < argc; i += 2) {
        hash.$store(argv[i], argv[i + 1]);
      }

      return hash;
    }, -1);
    $defs(self, '$allocate', function $$allocate() {
      var self = this;

      
      var hash = new self.$$constructor();

      $hash_init(hash);

      hash.$$none = nil;
      hash.$$proc = nil;

      return hash;
    
    });
    $defs(self, '$try_convert', function $$try_convert(obj) {
      
      return $Opal['$coerce_to?'](obj, $$$('Hash'), "to_hash")
    });
    
    $def(self, '$initialize', function $$initialize(defaults) {
      var block = $$initialize.$$p || nil, self = this;

      $$initialize.$$p = null;
      
      $deny_frozen_access(self);

      if (defaults !== undefined && block !== nil) {
        $Kernel.$raise($$$('ArgumentError'), "wrong number of arguments (1 for 0)");
      }
      self.$$none = (defaults === undefined ? nil : defaults);
      self.$$proc = block;

      return self;
    }, -1);
    
    $def(self, '$==', function $Hash_$eq_eq$2(other) {
      var self = this;

      
      if (self === other) {
        return true;
      }

      if (!other.$$is_hash) {
        return false;
      }

      if (self.$$keys.length !== other.$$keys.length) {
        return false;
      }

      for (var i = 0, keys = self.$$keys, length = keys.length, key, value, other_value; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          value = self.$$smap[key];
          other_value = other.$$smap[key];
        } else {
          value = key.value;
          other_value = $hash_get(other, key.key);
        }

        if (other_value === undefined || !value['$eql?'](other_value)) {
          return false;
        }
      }

      return true;
    
    });
    
    $def(self, '$>=', function $Hash_$gt_eq$3(other) {
      var self = this, result = nil;

      
      other = $Opal['$coerce_to!'](other, $$$('Hash'), "to_hash");
      
      if (self.$$keys.length < other.$$keys.length) {
        return false
      }
      result = true;
      $send(other, 'each', [], function $$4(other_key, other_val){var self = $$4.$$s == null ? this : $$4.$$s, val = nil;

        
        if (other_key == null) other_key = nil;
        if (other_val == null) other_val = nil;
        val = self.$fetch(other_key, null);
        
        if (val == null || val !== other_val) {
          result = false;
          return;
        }
}, {$$s: self});
      return result;
    });
    
    $def(self, '$>', function $Hash_$gt$5(other) {
      var self = this;

      
      other = $Opal['$coerce_to!'](other, $$$('Hash'), "to_hash");
      
      if (self.$$keys.length <= other.$$keys.length) {
        return false
      }
      return $rb_ge(self, other);
    });
    
    $def(self, '$<', function $Hash_$lt$6(other) {
      var self = this;

      
      other = $Opal['$coerce_to!'](other, $$$('Hash'), "to_hash");
      return $rb_gt(other, self);
    });
    
    $def(self, '$<=', function $Hash_$lt_eq$7(other) {
      var self = this;

      
      other = $Opal['$coerce_to!'](other, $$$('Hash'), "to_hash");
      return $rb_ge(other, self);
    });
    
    $def(self, '$[]', function $Hash_$$$8(key) {
      var self = this;

      
      var value = $hash_get(self, key);

      if (value !== undefined) {
        return value;
      }

      return self.$default(key);
    
    });
    
    $def(self, '$[]=', function $Hash_$$$eq$9(key, value) {
      var self = this;

      
      $deny_frozen_access(self);

      $hash_put(self, key, value);
      return value;
    
    });
    
    $def(self, '$assoc', function $$assoc(object) {
      var self = this;

      
      for (var i = 0, keys = self.$$keys, length = keys.length, key; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          if ((key)['$=='](object)) {
            return [key, self.$$smap[key]];
          }
        } else {
          if ((key.key)['$=='](object)) {
            return [key.key, key.value];
          }
        }
      }

      return nil;
    
    });
    
    $def(self, '$clear', function $$clear() {
      var self = this;

      
      $deny_frozen_access(self);

      $hash_init(self);
      return self;
    
    });
    
    $def(self, '$clone', function $$clone() {
      var self = this;

      
      var hash = new self.$$class();

      $hash_init(hash);
      Opal.hash_clone(self, hash);

      return hash;
    
    });
    
    $def(self, '$compact', function $$compact() {
      var self = this;

      
      var hash = $hash();

      for (var i = 0, keys = self.$$keys, length = keys.length, key, value, obj; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          value = self.$$smap[key];
        } else {
          value = key.value;
          key = key.key;
        }

        if (value !== nil) {
          $hash_put(hash, key, value);
        }
      }

      return hash;
    
    });
    
    $def(self, '$compact!', function $Hash_compact$excl$10() {
      var self = this;

      
      $deny_frozen_access(self);

      var changes_were_made = false;

      for (var i = 0, keys = self.$$keys, length = keys.length, key, value, obj; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          value = self.$$smap[key];
        } else {
          value = key.value;
          key = key.key;
        }

        if (value === nil) {
          if ($hash_delete(self, key) !== undefined) {
            changes_were_made = true;
            length--;
            i--;
          }
        }
      }

      return changes_were_made ? self : nil;
    
    });
    
    $def(self, '$compare_by_identity', function $$compare_by_identity() {
      var self = this;

      
      $deny_frozen_access(self);

      var i, ii, key, keys = self.$$keys, identity_hash;

      if (self.$$by_identity) return self;
      if (self.$$keys.length === 0) {
        self.$$by_identity = true;
        return self;
      }

      identity_hash = $hash2([], {}).$compare_by_identity();
      for(i = 0, ii = keys.length; i < ii; i++) {
        key = keys[i];
        if (!key.$$is_string) key = key.key;
        $hash_put(identity_hash, key, $hash_get(self, key));
      }

      self.$$by_identity = true;
      self.$$map = identity_hash.$$map;
      self.$$smap = identity_hash.$$smap;
      return self;
    
    });
    
    $def(self, '$compare_by_identity?', function $Hash_compare_by_identity$ques$11() {
      var self = this;

      return self.$$by_identity === true;
    });
    
    $def(self, '$default', function $Hash_default$12(key) {
      var self = this;
      
      if (key !== undefined && self.$$proc !== nil && self.$$proc !== undefined) {
        return self.$$proc.$call(self, key);
      }
      if (self.$$none === undefined) {
        return nil;
      }
      return self.$$none;
    }, -1);
    
    $def(self, '$default=', function $Hash_default$eq$13(object) {
      var self = this;

      
      $deny_frozen_access(self);

      self.$$proc = nil;
      self.$$none = object;

      return object;
    
    });
    
    $def(self, '$default_proc', function $$default_proc() {
      var self = this;

      
      if (self.$$proc !== undefined) {
        return self.$$proc;
      }
      return nil;
    
    });
    
    $def(self, '$default_proc=', function $Hash_default_proc$eq$14(default_proc) {
      var self = this;

      
      $deny_frozen_access(self);

      var proc = default_proc;

      if (proc !== nil) {
        proc = $Opal['$coerce_to!'](proc, $$$('Proc'), "to_proc");

        if ((proc)['$lambda?']() && (proc).$arity().$abs() !== 2) {
          $Kernel.$raise($$$('TypeError'), "default_proc takes two arguments");
        }
      }

      self.$$none = nil;
      self.$$proc = proc;

      return default_proc;
    
    });
    
    $def(self, '$delete', function $Hash_delete$15(key) {
      var block = $Hash_delete$15.$$p || nil, self = this;

      $Hash_delete$15.$$p = null;
      
      $deny_frozen_access(self);
      var value = $hash_delete(self, key);

      if (value !== undefined) {
        return value;
      }

      if (block !== nil) {
        return Opal.yield1(block, key);
      }

      return nil;
    });
    
    $def(self, '$delete_if', function $$delete_if() {
      var block = $$delete_if.$$p || nil, self = this;

      $$delete_if.$$p = null;
      if (!$truthy(block)) {
        return $send(self, 'enum_for', ["delete_if"], function $$16(){var self = $$16.$$s == null ? this : $$16.$$s;

          return self.$size()}, {$$s: self})
      }      
      $deny_frozen_access(self);

      for (var i = 0, keys = self.$$keys, length = keys.length, key, value, obj; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          value = self.$$smap[key];
        } else {
          value = key.value;
          key = key.key;
        }

        obj = block(key, value);

        if (obj !== false && obj !== nil) {
          if ($hash_delete(self, key) !== undefined) {
            length--;
            i--;
          }
        }
      }

      return self;
    });
    
    $def(self, '$dig', function $$dig(key, $a) {
      var $post_args, keys, self = this, item = nil;

      
      $post_args = $slice(arguments, 1);
      keys = $post_args;
      item = self['$[]'](key);
      
      if (item === nil || keys.length === 0) {
        return item;
      }
      if (!$truthy(item['$respond_to?']("dig"))) {
        $Kernel.$raise($$$('TypeError'), "" + (item.$class()) + " does not have #dig method");
      }      return $send(item, 'dig', $to_a(keys));
    }, -2);
    
    $def(self, '$each', function $$each() {
      var block = $$each.$$p || nil, self = this;

      $$each.$$p = null;
      if (!$truthy(block)) {
        return $send(self, 'enum_for', ["each"], function $$17(){var self = $$17.$$s == null ? this : $$17.$$s;

          return self.$size()}, {$$s: self})
      }      
      for (var i = 0, keys = self.$$keys.slice(), length = keys.length, key, value; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          value = self.$$smap[key];
        } else {
          value = key.value;
          key = key.key;
        }

        $yield1(block, [key, value]);
      }

      return self;
    });
    
    $def(self, '$each_key', function $$each_key() {
      var block = $$each_key.$$p || nil, self = this;

      $$each_key.$$p = null;
      if (!$truthy(block)) {
        return $send(self, 'enum_for', ["each_key"], function $$18(){var self = $$18.$$s == null ? this : $$18.$$s;

          return self.$size()}, {$$s: self})
      }      
      for (var i = 0, keys = self.$$keys.slice(), length = keys.length, key; i < length; i++) {
        key = keys[i];

        block(key.$$is_string ? key : key.key);
      }

      return self;
    });
    
    $def(self, '$each_value', function $$each_value() {
      var block = $$each_value.$$p || nil, self = this;

      $$each_value.$$p = null;
      if (!$truthy(block)) {
        return $send(self, 'enum_for', ["each_value"], function $$19(){var self = $$19.$$s == null ? this : $$19.$$s;

          return self.$size()}, {$$s: self})
      }      
      for (var i = 0, keys = self.$$keys.slice(), length = keys.length, key; i < length; i++) {
        key = keys[i];

        block(key.$$is_string ? self.$$smap[key] : key.value);
      }

      return self;
    });
    
    $def(self, '$empty?', function $Hash_empty$ques$20() {
      var self = this;

      return self.$$keys.length === 0;
    });
    
    $def(self, '$except', function $$except($a) {
      var $post_args, keys, self = this;

      
      $post_args = $slice(arguments);
      keys = $post_args;
      return $send(self.$dup(), 'except!', $to_a(keys));
    }, -1);
    
    $def(self, '$except!', function $Hash_except$excl$21($a) {
      var $post_args, keys, self = this;

      
      $post_args = $slice(arguments);
      keys = $post_args;
      $send(keys, 'each', [], function $$22(key){var self = $$22.$$s == null ? this : $$22.$$s;

        
        if (key == null) key = nil;
        return self.$delete(key);}, {$$s: self});
      return self;
    }, -1);
    
    $def(self, '$fetch', function $$fetch(key, defaults) {
      var block = $$fetch.$$p || nil, self = this;

      $$fetch.$$p = null;
      
      var value = $hash_get(self, key);

      if (value !== undefined) {
        return value;
      }

      if (block !== nil) {
        return block(key);
      }

      if (defaults !== undefined) {
        return defaults;
      }
      return $Kernel.$raise($$$('KeyError').$new("key not found: " + (key.$inspect()), $hash2(["key", "receiver"], {"key": key, "receiver": self})));
    }, -2);
    
    $def(self, '$fetch_values', function $$fetch_values($a) {
      var block = $$fetch_values.$$p || nil, $post_args, keys, self = this;

      $$fetch_values.$$p = null;
      $post_args = $slice(arguments);
      keys = $post_args;
      return $send(keys, 'map', [], function $$23(key){var self = $$23.$$s == null ? this : $$23.$$s;

        
        if (key == null) key = nil;
        return $send(self, 'fetch', [key], block.$to_proc());}, {$$s: self});
    }, -1);
    
    $def(self, '$flatten', function $$flatten(level) {
      var self = this;

      
      if (level == null) level = 1;
      level = $Opal['$coerce_to!'](level, $$$('Integer'), "to_int");
      
      var result = [];

      for (var i = 0, keys = self.$$keys, length = keys.length, key, value; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          value = self.$$smap[key];
        } else {
          value = key.value;
          key = key.key;
        }

        result.push(key);

        if (value.$$is_array) {
          if (level === 1) {
            result.push(value);
            continue;
          }

          result = result.concat((value).$flatten(level - 2));
          continue;
        }

        result.push(value);
      }

      return result;
    }, -1);
    
    $def(self, '$freeze', function $$freeze() {
      var self = this;

      
      if ($truthy(self['$frozen?']())) {
        return self
      }      return $freeze(self);    });
    
    $def(self, '$has_key?', function $Hash_has_key$ques$24(key) {
      var self = this;

      return $hash_get(self, key) !== undefined;
    });
    
    $def(self, '$has_value?', function $Hash_has_value$ques$25(value) {
      var self = this;

      
      for (var i = 0, keys = self.$$keys, length = keys.length, key; i < length; i++) {
        key = keys[i];

        if (((key.$$is_string ? self.$$smap[key] : key.value))['$=='](value)) {
          return true;
        }
      }

      return false;
    
    });
    
    $def(self, '$hash', function $$hash() {
      var self = this;

      
      var top = (Opal.hash_ids === undefined),
          hash_id = self.$object_id(),
          result = ['Hash'],
          key, item;

      try {
        if (top) {
          Opal.hash_ids = Object.create(null);
        }

        if (Opal[hash_id]) {
          return 'self';
        }

        for (key in Opal.hash_ids) {
          item = Opal.hash_ids[key];
          if (self['$eql?'](item)) {
            return 'self';
          }
        }

        Opal.hash_ids[hash_id] = self;

        for (var i = 0, keys = self.$$keys, length = keys.length; i < length; i++) {
          key = keys[i];

          if (key.$$is_string) {
            result.push([key, self.$$smap[key].$hash()]);
          } else {
            result.push([key.key_hash, key.value.$hash()]);
          }
        }

        return result.sort().join();

      } finally {
        if (top) {
          Opal.hash_ids = undefined;
        }
      }
    
    });
    
    $def(self, '$index', function $$index(object) {
      var self = this;

      
      for (var i = 0, keys = self.$$keys, length = keys.length, key, value; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          value = self.$$smap[key];
        } else {
          value = key.value;
          key = key.key;
        }

        if ((value)['$=='](object)) {
          return key;
        }
      }

      return nil;
    
    });
    
    $def(self, '$indexes', function $$indexes($a) {
      var $post_args, args, self = this;

      
      $post_args = $slice(arguments);
      args = $post_args;
      
      var result = [];

      for (var i = 0, length = args.length, key, value; i < length; i++) {
        key = args[i];
        value = $hash_get(self, key);

        if (value === undefined) {
          result.push(self.$default());
          continue;
        }

        result.push(value);
      }

      return result;
    }, -1);
    var inspect_ids;
    
    $def(self, '$inspect', function $$inspect() {
      var self = this;

      
      
      var top = (inspect_ids === undefined),
          hash_id = self.$object_id(),
          result = [];
      
      return (function() { try {
      
      
        if (top) {
          inspect_ids = {};
        }

        if (inspect_ids.hasOwnProperty(hash_id)) {
          return '{...}';
        }

        inspect_ids[hash_id] = true;

        for (var i = 0, keys = self.$$keys, length = keys.length, key, value; i < length; i++) {
          key = keys[i];

          if (key.$$is_string) {
            value = self.$$smap[key];
          } else {
            value = key.value;
            key = key.key;
          }

          key = $$('Opal').$inspect(key);
          value = $$('Opal').$inspect(value);

          result.push(key + '=>' + value);
        }

        return '{' + result.join(', ') + '}';
      ;
      return nil;
      } finally {
        if (top) inspect_ids = undefined;
      } })();    });
    
    $def(self, '$invert', function $$invert() {
      var self = this;

      
      var hash = $hash();

      for (var i = 0, keys = self.$$keys, length = keys.length, key, value; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          value = self.$$smap[key];
        } else {
          value = key.value;
          key = key.key;
        }

        $hash_put(hash, value, key);
      }

      return hash;
    
    });
    
    $def(self, '$keep_if', function $$keep_if() {
      var block = $$keep_if.$$p || nil, self = this;

      $$keep_if.$$p = null;
      if (!$truthy(block)) {
        return $send(self, 'enum_for', ["keep_if"], function $$26(){var self = $$26.$$s == null ? this : $$26.$$s;

          return self.$size()}, {$$s: self})
      }      
      $deny_frozen_access(self);

      for (var i = 0, keys = self.$$keys, length = keys.length, key, value, obj; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          value = self.$$smap[key];
        } else {
          value = key.value;
          key = key.key;
        }

        obj = block(key, value);

        if (obj === false || obj === nil) {
          if ($hash_delete(self, key) !== undefined) {
            length--;
            i--;
          }
        }
      }

      return self;
    });
    
    $def(self, '$keys', function $$keys() {
      var self = this;

      
      var result = [];

      for (var i = 0, keys = self.$$keys, length = keys.length, key; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          result.push(key);
        } else {
          result.push(key.key);
        }
      }

      return result;
    
    });
    
    $def(self, '$length', function $$length() {
      var self = this;

      return self.$$keys.length;
    });
    
    $def(self, '$merge', function $$merge($a) {
      var block = $$merge.$$p || nil, $post_args, others, self = this;

      $$merge.$$p = null;
      $post_args = $slice(arguments);
      others = $post_args;
      return $send(self.$dup(), 'merge!', $to_a(others), block.$to_proc());
    }, -1);
    
    $def(self, '$merge!', function $Hash_merge$excl$27($a) {
      var block = $Hash_merge$excl$27.$$p || nil, $post_args, others, self = this;

      $Hash_merge$excl$27.$$p = null;
      $post_args = $slice(arguments);
      others = $post_args;
      
      $deny_frozen_access(self);
      var i, j, other, other_keys, length, key, value, other_value;
      for (i = 0; i < others.length; ++i) {
        other = $Opal['$coerce_to!'](others[i], $$$('Hash'), "to_hash");
        other_keys = other.$$keys, length = other_keys.length;

        if (block === nil) {
          for (j = 0; j < length; j++) {
            key = other_keys[j];

            if (key.$$is_string) {
              other_value = other.$$smap[key];
            } else {
              other_value = key.value;
              key = key.key;
            }

            $hash_put(self, key, other_value);
          }
        } else {
          for (j = 0; j < length; j++) {
            key = other_keys[j];

            if (key.$$is_string) {
              other_value = other.$$smap[key];
            } else {
              other_value = key.value;
              key = key.key;
            }

            value = $hash_get(self, key);

            if (value === undefined) {
              $hash_put(self, key, other_value);
              continue;
            }

            $hash_put(self, key, block(key, value, other_value));
          }
        }
      }

      return self;
    }, -1);
    
    $def(self, '$rassoc', function $$rassoc(object) {
      var self = this;

      
      for (var i = 0, keys = self.$$keys, length = keys.length, key, value; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          value = self.$$smap[key];
        } else {
          value = key.value;
          key = key.key;
        }

        if ((value)['$=='](object)) {
          return [key, value];
        }
      }

      return nil;
    
    });
    
    $def(self, '$rehash', function $$rehash() {
      var self = this;

      
      $deny_frozen_access(self);
      Opal.hash_rehash(self);
      return self;
    
    });
    
    $def(self, '$reject', function $$reject() {
      var block = $$reject.$$p || nil, self = this;

      $$reject.$$p = null;
      if (!$truthy(block)) {
        return $send(self, 'enum_for', ["reject"], function $$28(){var self = $$28.$$s == null ? this : $$28.$$s;

          return self.$size()}, {$$s: self})
      }      
      var hash = $hash();

      for (var i = 0, keys = self.$$keys, length = keys.length, key, value, obj; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          value = self.$$smap[key];
        } else {
          value = key.value;
          key = key.key;
        }

        obj = block(key, value);

        if (obj === false || obj === nil) {
          $hash_put(hash, key, value);
        }
      }

      return hash;
    });
    
    $def(self, '$reject!', function $Hash_reject$excl$29() {
      var block = $Hash_reject$excl$29.$$p || nil, self = this;

      $Hash_reject$excl$29.$$p = null;
      if (!$truthy(block)) {
        return $send(self, 'enum_for', ["reject!"], function $$30(){var self = $$30.$$s == null ? this : $$30.$$s;

          return self.$size()}, {$$s: self})
      }      
      $deny_frozen_access(self);

      var changes_were_made = false;

      for (var i = 0, keys = self.$$keys, length = keys.length, key, value, obj; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          value = self.$$smap[key];
        } else {
          value = key.value;
          key = key.key;
        }

        obj = block(key, value);

        if (obj !== false && obj !== nil) {
          if ($hash_delete(self, key) !== undefined) {
            changes_were_made = true;
            length--;
            i--;
          }
        }
      }

      return changes_were_made ? self : nil;
    });
    
    $def(self, '$replace', function $$replace(other) {
      var self = this;

      
      $deny_frozen_access(self);      other = $Opal['$coerce_to!'](other, $$$('Hash'), "to_hash");
      
      $hash_init(self);

      for (var i = 0, other_keys = other.$$keys, length = other_keys.length, key, value, other_value; i < length; i++) {
        key = other_keys[i];

        if (key.$$is_string) {
          other_value = other.$$smap[key];
        } else {
          other_value = key.value;
          key = key.key;
        }

        $hash_put(self, key, other_value);
      }
      if ($truthy(other.$default_proc())) {
        self['$default_proc='](other.$default_proc());
      } else {
        self['$default='](other.$default());
      }      return self;
    });
    
    $def(self, '$select', function $$select() {
      var block = $$select.$$p || nil, self = this;

      $$select.$$p = null;
      if (!$truthy(block)) {
        return $send(self, 'enum_for', ["select"], function $$31(){var self = $$31.$$s == null ? this : $$31.$$s;

          return self.$size()}, {$$s: self})
      }      
      var hash = $hash();

      for (var i = 0, keys = self.$$keys, length = keys.length, key, value, obj; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          value = self.$$smap[key];
        } else {
          value = key.value;
          key = key.key;
        }

        obj = block(key, value);

        if (obj !== false && obj !== nil) {
          $hash_put(hash, key, value);
        }
      }

      return hash;
    });
    
    $def(self, '$select!', function $Hash_select$excl$32() {
      var block = $Hash_select$excl$32.$$p || nil, self = this;

      $Hash_select$excl$32.$$p = null;
      if (!$truthy(block)) {
        return $send(self, 'enum_for', ["select!"], function $$33(){var self = $$33.$$s == null ? this : $$33.$$s;

          return self.$size()}, {$$s: self})
      }      
      $deny_frozen_access(self);

      var result = nil;

      for (var i = 0, keys = self.$$keys, length = keys.length, key, value, obj; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          value = self.$$smap[key];
        } else {
          value = key.value;
          key = key.key;
        }

        obj = block(key, value);

        if (obj === false || obj === nil) {
          if ($hash_delete(self, key) !== undefined) {
            length--;
            i--;
          }
          result = self;
        }
      }

      return result;
    });
    
    $def(self, '$shift', function $$shift() {
      var self = this;

      
      $deny_frozen_access(self);
      var keys = self.$$keys,
          key;

      if (keys.length > 0) {
        key = keys[0];

        key = key.$$is_string ? key : key.key;

        return [key, $hash_delete(self, key)];
      }

      return nil;
    
    });
    
    $def(self, '$slice', function $$slice($a) {
      var $post_args, keys, self = this;

      
      $post_args = $slice(arguments);
      keys = $post_args;
      
      var result = $hash();

      for (var i = 0, length = keys.length; i < length; i++) {
        var key = keys[i], value = $hash_get(self, key);

        if (value !== undefined) {
          $hash_put(result, key, value);
        }
      }

      return result;
    }, -1);
    
    $def(self, '$to_a', function $$to_a() {
      var self = this;

      
      var result = [];

      for (var i = 0, keys = self.$$keys, length = keys.length, key, value; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          value = self.$$smap[key];
        } else {
          value = key.value;
          key = key.key;
        }

        result.push([key, value]);
      }

      return result;
    
    });
    
    $def(self, '$to_h', function $$to_h() {
      var block = $$to_h.$$p || nil, self = this;

      $$to_h.$$p = null;
      if ((block !== nil)) {
        return $send(self, 'map', [], block.$to_proc()).$to_h()
      }      
      if (self.$$class === Opal.Hash) {
        return self;
      }

      var hash = new Opal.Hash();

      $hash_init(hash);
      Opal.hash_clone(self, hash);

      return hash;
    });
    
    $def(self, '$to_hash', $return_self);
    
    $def(self, '$to_proc', function $$to_proc() {
      var self = this;

      return $send(self, 'proc', [], function $$34(key){var self = $$34.$$s == null ? this : $$34.$$s;
        
        if (key == null) {
          $Kernel.$raise($$$('ArgumentError'), "no key given");
        }
        return self['$[]'](key);}, {$$arity: -1, $$s: self})
    });
    
    $def(self, '$transform_keys', function $$transform_keys() {
      var block = $$transform_keys.$$p || nil, self = this;

      $$transform_keys.$$p = null;
      if (!$truthy(block)) {
        return $send(self, 'enum_for', ["transform_keys"], function $$35(){var self = $$35.$$s == null ? this : $$35.$$s;

          return self.$size()}, {$$s: self})
      }      
      var result = $hash();

      for (var i = 0, keys = self.$$keys, length = keys.length, key, value; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          value = self.$$smap[key];
        } else {
          value = key.value;
          key = key.key;
        }

        key = $yield1(block, key);

        $hash_put(result, key, value);
      }

      return result;
    });
    
    $def(self, '$transform_keys!', function $Hash_transform_keys$excl$36() {
      var block = $Hash_transform_keys$excl$36.$$p || nil, self = this;

      $Hash_transform_keys$excl$36.$$p = null;
      if (!$truthy(block)) {
        return $send(self, 'enum_for', ["transform_keys!"], function $$37(){var self = $$37.$$s == null ? this : $$37.$$s;

          return self.$size()}, {$$s: self})
      }      
      $deny_frozen_access(self);

      var keys = Opal.slice(self.$$keys),
          i, length = keys.length, key, value, new_key;

      for (i = 0; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          value = self.$$smap[key];
        } else {
          value = key.value;
          key = key.key;
        }

        new_key = $yield1(block, key);

        $hash_delete(self, key);
        $hash_put(self, new_key, value);
      }

      return self;
    });
    
    $def(self, '$transform_values', function $$transform_values() {
      var block = $$transform_values.$$p || nil, self = this;

      $$transform_values.$$p = null;
      if (!$truthy(block)) {
        return $send(self, 'enum_for', ["transform_values"], function $$38(){var self = $$38.$$s == null ? this : $$38.$$s;

          return self.$size()}, {$$s: self})
      }      
      var result = $hash();

      for (var i = 0, keys = self.$$keys, length = keys.length, key, value; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          value = self.$$smap[key];
        } else {
          value = key.value;
          key = key.key;
        }

        value = $yield1(block, value);

        $hash_put(result, key, value);
      }

      return result;
    });
    
    $def(self, '$transform_values!', function $Hash_transform_values$excl$39() {
      var block = $Hash_transform_values$excl$39.$$p || nil, self = this;

      $Hash_transform_values$excl$39.$$p = null;
      if (!$truthy(block)) {
        return $send(self, 'enum_for', ["transform_values!"], function $$40(){var self = $$40.$$s == null ? this : $$40.$$s;

          return self.$size()}, {$$s: self})
      }      
      $deny_frozen_access(self);

      for (var i = 0, keys = self.$$keys, length = keys.length, key, value; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          value = self.$$smap[key];
        } else {
          value = key.value;
          key = key.key;
        }

        value = $yield1(block, value);

        $hash_put(self, key, value);
      }

      return self;
    });
    
    $def(self, '$values', function $$values() {
      var self = this;

      
      var result = [];

      for (var i = 0, keys = self.$$keys, length = keys.length, key; i < length; i++) {
        key = keys[i];

        if (key.$$is_string) {
          result.push(self.$$smap[key]);
        } else {
          result.push(key.value);
        }
      }

      return result;
    
    });
    $alias(self, "dup", "clone");
    $alias(self, "each_pair", "each");
    $alias(self, "eql?", "==");
    $alias(self, "filter", "select");
    $alias(self, "filter!", "select!");
    $alias(self, "include?", "has_key?");
    $alias(self, "indices", "indexes");
    $alias(self, "key", "index");
    $alias(self, "key?", "has_key?");
    $alias(self, "member?", "has_key?");
    $alias(self, "size", "length");
    $alias(self, "store", "[]=");
    $alias(self, "to_s", "inspect");
    $alias(self, "update", "merge!");
    $alias(self, "value?", "has_value?");
    return $alias(self, "values_at", "indexes");
  })('::', null, $nesting);
};

Opal.modules["corelib/number"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $Opal = Opal.Opal, $Kernel = Opal.Kernel, $def = Opal.def, $eqeqeq = Opal.eqeqeq, $truthy = Opal.truthy, $rb_gt = Opal.rb_gt, $not = Opal.not, $rb_lt = Opal.rb_lt, $alias = Opal.alias, $send2 = Opal.send2, $find_super = Opal.find_super, $send = Opal.send, $rb_plus = Opal.rb_plus, $rb_minus = Opal.rb_minus, $eqeq = Opal.eqeq, $return_self = Opal.return_self, $rb_divide = Opal.rb_divide, $to_ary = Opal.to_ary, $rb_times = Opal.rb_times, $rb_le = Opal.rb_le, $rb_ge = Opal.rb_ge, $return_val = Opal.return_val, $const_set = Opal.const_set, self = Opal.top, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('require,bridge,raise,name,class,Float,respond_to?,coerce_to!,__coerced__,===,>,!,**,new,<,to_f,==,nan?,infinite?,enum_for,+,-,gcd,lcm,%,/,frexp,to_i,ldexp,rationalize,*,<<,to_r,truncate,-@,size,<=,>=,inspect,angle,to_s,is_a?,abs,__id__,next,coerce_to?');
  
  self.$require("corelib/numeric");
  (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Number');

    
    $Opal.$bridge(Number, self);
    Opal.prop(self.$$prototype, '$$is_number', true);
    self.$$is_number_class = true;
    (function(self, $parent_nesting) {
      
      
      
      $def(self, '$allocate', function $$allocate() {
        var self = this;

        return $Kernel.$raise($$$('TypeError'), "allocator undefined for " + (self.$name()))
      });
      
      
      Opal.udef(self, '$' + "new");      return nil;    })(Opal.get_singleton_class(self));
    
    $def(self, '$coerce', function $$coerce(other) {
      var self = this;

      
      if (other === nil) {
        $Kernel.$raise($$$('TypeError'), "can't convert " + (other.$class()) + " into Float");
      }
      else if (other.$$is_string) {
        return [$Kernel.$Float(other), self];
      }
      else if (other['$respond_to?']("to_f")) {
        return [$Opal['$coerce_to!'](other, $$$('Float'), "to_f"), self];
      }
      else if (other.$$is_number) {
        return [other, self];
      }
      else {
        $Kernel.$raise($$$('TypeError'), "can't convert " + (other.$class()) + " into Float");
      }
    
    });
    
    $def(self, '$__id__', function $$__id__() {
      var self = this;

      return (self * 2) + 1;
    });
    
    $def(self, '$+', function $Number_$plus$1(other) {
      var self = this;

      
      if (other.$$is_number) {
        return self + other;
      }
      else {
        return self.$__coerced__("+", other);
      }
    
    });
    
    $def(self, '$-', function $Number_$minus$2(other) {
      var self = this;

      
      if (other.$$is_number) {
        return self - other;
      }
      else {
        return self.$__coerced__("-", other);
      }
    
    });
    
    $def(self, '$*', function $Number_$$3(other) {
      var self = this;

      
      if (other.$$is_number) {
        return self * other;
      }
      else {
        return self.$__coerced__("*", other);
      }
    
    });
    
    $def(self, '$/', function $Number_$slash$4(other) {
      var self = this;

      
      if (other.$$is_number) {
        return self / other;
      }
      else {
        return self.$__coerced__("/", other);
      }
    
    });
    
    $def(self, '$%', function $Number_$percent$5(other) {
      var self = this;

      
      if (other.$$is_number) {
        if (other == -Infinity) {
          return other;
        }
        else if (other == 0) {
          $Kernel.$raise($$$('ZeroDivisionError'), "divided by 0");
        }
        else if (other < 0 || self < 0) {
          return (self % other + other) % other;
        }
        else {
          return self % other;
        }
      }
      else {
        return self.$__coerced__("%", other);
      }
    
    });
    
    $def(self, '$&', function $Number_$$6(other) {
      var self = this;

      
      if (other.$$is_number) {
        return self & other;
      }
      else {
        return self.$__coerced__("&", other);
      }
    
    });
    
    $def(self, '$|', function $Number_$$7(other) {
      var self = this;

      
      if (other.$$is_number) {
        return self | other;
      }
      else {
        return self.$__coerced__("|", other);
      }
    
    });
    
    $def(self, '$^', function $Number_$$8(other) {
      var self = this;

      
      if (other.$$is_number) {
        return self ^ other;
      }
      else {
        return self.$__coerced__("^", other);
      }
    
    });
    
    $def(self, '$<', function $Number_$lt$9(other) {
      var self = this;

      
      if (other.$$is_number) {
        return self < other;
      }
      else {
        return self.$__coerced__("<", other);
      }
    
    });
    
    $def(self, '$<=', function $Number_$lt_eq$10(other) {
      var self = this;

      
      if (other.$$is_number) {
        return self <= other;
      }
      else {
        return self.$__coerced__("<=", other);
      }
    
    });
    
    $def(self, '$>', function $Number_$gt$11(other) {
      var self = this;

      
      if (other.$$is_number) {
        return self > other;
      }
      else {
        return self.$__coerced__(">", other);
      }
    
    });
    
    $def(self, '$>=', function $Number_$gt_eq$12(other) {
      var self = this;

      
      if (other.$$is_number) {
        return self >= other;
      }
      else {
        return self.$__coerced__(">=", other);
      }
    
    });
    
    var spaceship_operator = function(self, other) {
      if (other.$$is_number) {
        if (isNaN(self) || isNaN(other)) {
          return nil;
        }

        if (self > other) {
          return 1;
        } else if (self < other) {
          return -1;
        } else {
          return 0;
        }
      }
      else {
        return self.$__coerced__("<=>", other);
      }
    }
  ;
    
    $def(self, '$<=>', function $Number_$lt_eq_gt$13(other) {
      var self = this;

      try {
        return spaceship_operator(self, other);
      } catch ($err) {
        if (Opal.rescue($err, [$$$('ArgumentError')])) {
          try {
            return nil
          } finally { Opal.pop_exception(); }
        } else { throw $err; }
      }
    });
    
    $def(self, '$<<', function $Number_$lt$lt$14(count) {
      var self = this;

      
      count = $Opal['$coerce_to!'](count, $$$('Integer'), "to_int");
      return count > 0 ? self << count : self >> -count;
    });
    
    $def(self, '$>>', function $Number_$gt$gt$15(count) {
      var self = this;

      
      count = $Opal['$coerce_to!'](count, $$$('Integer'), "to_int");
      return count > 0 ? self >> count : self << -count;
    });
    
    $def(self, '$[]', function $Number_$$$16(bit) {
      var self = this;

      
      bit = $Opal['$coerce_to!'](bit, $$$('Integer'), "to_int");
      
      if (bit < 0) {
        return 0;
      }
      if (bit >= 32) {
        return self < 0 ? 1 : 0;
      }
      return (self >> bit) & 1;
    });
    
    $def(self, '$+@', function $Number_$plus$$17() {
      var self = this;

      return +self;
    });
    
    $def(self, '$-@', function $Number_$minus$$18() {
      var self = this;

      return -self;
    });
    
    $def(self, '$~', function $Number_$$19() {
      var self = this;

      return ~self;
    });
    
    $def(self, '$**', function $Number_$$$20(other) {
      var self = this;

      if ($eqeqeq($$$('Integer'), other)) {
        if (($not($$$('Integer')['$==='](self)) || ($truthy($rb_gt(other, 0))))) {
          return Math.pow(self, other);
        } else {
          return $$$('Rational').$new(self, 1)['$**'](other)
        }
      } else if (($rb_lt(self, 0) && (($eqeqeq($$$('Float'), other) || ($eqeqeq($$$('Rational'), other)))))) {
        return $$$('Complex').$new(self, 0)['$**'](other.$to_f())
      } else if ($truthy(other.$$is_number != null)) {
        return Math.pow(self, other);
      } else {
        return self.$__coerced__("**", other)
      }
    });
    
    $def(self, '$==', function $Number_$eq_eq$21(other) {
      var self = this;

      
      if (other.$$is_number) {
        return self.valueOf() === other.valueOf();
      }
      else if (other['$respond_to?']("==")) {
        return other['$=='](self);
      }
      else {
        return false;
      }
    
    });
    $alias(self, "===", "==");
    
    $def(self, '$abs', function $$abs() {
      var self = this;

      return Math.abs(self);
    });
    
    $def(self, '$abs2', function $$abs2() {
      var self = this;

      return Math.abs(self * self);
    });
    
    $def(self, '$allbits?', function $Number_allbits$ques$22(mask) {
      var self = this;

      
      mask = $Opal['$coerce_to!'](mask, $$$('Integer'), "to_int");
      return (self & mask) == mask;    });
    
    $def(self, '$anybits?', function $Number_anybits$ques$23(mask) {
      var self = this;

      
      mask = $Opal['$coerce_to!'](mask, $$$('Integer'), "to_int");
      return (self & mask) !== 0;    });
    
    $def(self, '$angle', function $$angle() {
      var self = this;

      
      if ($truthy(self['$nan?']())) {
        return self
      }      
      if (self == 0) {
        if (1 / self > 0) {
          return 0;
        }
        else {
          return Math.PI;
        }
      }
      else if (self < 0) {
        return Math.PI;
      }
      else {
        return 0;
      }
    });
    
    $def(self, '$bit_length', function $$bit_length() {
      var self = this;

      
      if (!$eqeqeq($$$('Integer'), self)) {
        $Kernel.$raise($$$('NoMethodError').$new("undefined method `bit_length` for " + (self) + ":Float", "bit_length"));
      }      
      if (self === 0 || self === -1) {
        return 0;
      }

      var result = 0,
          value  = self < 0 ? ~self : self;

      while (value != 0) {
        result   += 1;
        value  >>>= 1;
      }

      return result;
    });
    
    $def(self, '$ceil', function $$ceil(ndigits) {
      var self = this;

      
      if (ndigits == null) ndigits = 0;
      
      var f = self.$to_f();

      if (f % 1 === 0 && ndigits >= 0) {
        return f;
      }

      var factor = Math.pow(10, ndigits),
          result = Math.ceil(f * factor) / factor;

      if (f % 1 === 0) {
        result = Math.round(result);
      }

      return result;
    }, -1);
    
    $def(self, '$chr', function $$chr(encoding) {
      var self = this;
      return Opal.enc(String.fromCharCode(self), encoding || "BINARY");    }, -1);
    
    $def(self, '$denominator', function $$denominator() {
      var $yield = $$denominator.$$p || nil, self = this;

      $$denominator.$$p = null;
      if (($truthy(self['$nan?']()) || ($truthy(self['$infinite?']())))) {
        return 1
      } else {
        return $send2(self, $find_super(self, 'denominator', $$denominator, false, true), 'denominator', [], $yield)
      }
    });
    
    $def(self, '$downto', function $$downto(stop) {
      var block = $$downto.$$p || nil, self = this;

      $$downto.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["downto", stop], function $$24(){var self = $$24.$$s == null ? this : $$24.$$s;

          
          if (!$eqeqeq($$$('Numeric'), stop)) {
            $Kernel.$raise($$$('ArgumentError'), "comparison of " + (self.$class()) + " with " + (stop.$class()) + " failed");
          }          if ($truthy($rb_gt(stop, self))) {
            return 0
          } else {
            return $rb_plus($rb_minus(self, stop), 1)
          }}, {$$s: self})
      }      
      if (!stop.$$is_number) {
        $Kernel.$raise($$$('ArgumentError'), "comparison of " + (self.$class()) + " with " + (stop.$class()) + " failed");
      }
      for (var i = self; i >= stop; i--) {
        block(i);
      }
      return self;
    });
    
    $def(self, '$equal?', function $Number_equal$ques$25(other) {
      var self = this, $ret_or_1 = nil;

      if ($truthy(($ret_or_1 = self['$=='](other)))) {
        return $ret_or_1
      } else {
        return isNaN(self) && isNaN(other);
      }
    });
    
    $def(self, '$even?', function $Number_even$ques$26() {
      var self = this;

      return self % 2 === 0;
    });
    
    $def(self, '$floor', function $$floor(ndigits) {
      var self = this;

      
      if (ndigits == null) ndigits = 0;
      
      var f = self.$to_f();

      if (f % 1 === 0 && ndigits >= 0) {
        return f;
      }

      var factor = Math.pow(10, ndigits),
          result = Math.floor(f * factor) / factor;

      if (f % 1 === 0) {
        result = Math.round(result);
      }

      return result;
    }, -1);
    
    $def(self, '$gcd', function $$gcd(other) {
      var self = this;

      
      if (!$eqeqeq($$$('Integer'), other)) {
        $Kernel.$raise($$$('TypeError'), "not an integer");
      }      
      var min = Math.abs(self),
          max = Math.abs(other);

      while (min > 0) {
        var tmp = min;

        min = max % min;
        max = tmp;
      }

      return max;
    });
    
    $def(self, '$gcdlcm', function $$gcdlcm(other) {
      var self = this;

      return [self.$gcd(other), self.$lcm(other)]
    });
    
    $def(self, '$integer?', function $Number_integer$ques$27() {
      var self = this;

      return self % 1 === 0;
    });
    
    $def(self, '$is_a?', function $Number_is_a$ques$28(klass) {
      var $yield = $Number_is_a$ques$28.$$p || nil, self = this;

      $Number_is_a$ques$28.$$p = null;
      
      if (($eqeq(klass, $$$('Integer')) && ($eqeqeq($$$('Integer'), self)))) {
        return true
      }      if (($eqeq(klass, $$$('Integer')) && ($eqeqeq($$$('Integer'), self)))) {
        return true
      }      if (($eqeq(klass, $$$('Float')) && ($eqeqeq($$$('Float'), self)))) {
        return true
      }      return $send2(self, $find_super(self, 'is_a?', $Number_is_a$ques$28, false, true), 'is_a?', [klass], $yield);
    });
    
    $def(self, '$instance_of?', function $Number_instance_of$ques$29(klass) {
      var $yield = $Number_instance_of$ques$29.$$p || nil, self = this;

      $Number_instance_of$ques$29.$$p = null;
      
      if (($eqeq(klass, $$$('Integer')) && ($eqeqeq($$$('Integer'), self)))) {
        return true
      }      if (($eqeq(klass, $$$('Integer')) && ($eqeqeq($$$('Integer'), self)))) {
        return true
      }      if (($eqeq(klass, $$$('Float')) && ($eqeqeq($$$('Float'), self)))) {
        return true
      }      return $send2(self, $find_super(self, 'instance_of?', $Number_instance_of$ques$29, false, true), 'instance_of?', [klass], $yield);
    });
    
    $def(self, '$lcm', function $$lcm(other) {
      var self = this;

      
      if (!$eqeqeq($$$('Integer'), other)) {
        $Kernel.$raise($$$('TypeError'), "not an integer");
      }      
      if (self == 0 || other == 0) {
        return 0;
      }
      else {
        return Math.abs(self * other / self.$gcd(other));
      }
    });
    
    $def(self, '$next', function $$next() {
      var self = this;

      return self + 1;
    });
    
    $def(self, '$nobits?', function $Number_nobits$ques$30(mask) {
      var self = this;

      
      mask = $Opal['$coerce_to!'](mask, $$$('Integer'), "to_int");
      return (self & mask) == 0;    });
    
    $def(self, '$nonzero?', function $Number_nonzero$ques$31() {
      var self = this;

      return self == 0 ? nil : self;
    });
    
    $def(self, '$numerator', function $$numerator() {
      var $yield = $$numerator.$$p || nil, self = this;

      $$numerator.$$p = null;
      if (($truthy(self['$nan?']()) || ($truthy(self['$infinite?']())))) {
        return self
      } else {
        return $send2(self, $find_super(self, 'numerator', $$numerator, false, true), 'numerator', [], $yield)
      }
    });
    
    $def(self, '$odd?', function $Number_odd$ques$32() {
      var self = this;

      return self % 2 !== 0;
    });
    
    $def(self, '$ord', $return_self);
    
    $def(self, '$pow', function $$pow(b, m) {
      var self = this;
      
      if (self == 0) {
        $Kernel.$raise($$$('ZeroDivisionError'), "divided by 0");
      }

      if (m === undefined) {
        return self['$**'](b);
      } else {
        if (!($$$('Integer')['$==='](b))) {
          $Kernel.$raise($$$('TypeError'), "Integer#pow() 2nd argument not allowed unless a 1st argument is integer");
        }

        if (b < 0) {
          $Kernel.$raise($$$('TypeError'), "Integer#pow() 1st argument cannot be negative when 2nd argument specified");
        }

        if (!($$$('Integer')['$==='](m))) {
          $Kernel.$raise($$$('TypeError'), "Integer#pow() 2nd argument not allowed unless all arguments are integers");
        }

        if (m === 0) {
          $Kernel.$raise($$$('ZeroDivisionError'), "divided by 0");
        }

        return self['$**'](b)['$%'](m)
      }
    }, -2);
    
    $def(self, '$pred', function $$pred() {
      var self = this;

      return self - 1;
    });
    
    $def(self, '$quo', function $$quo(other) {
      var $yield = $$quo.$$p || nil, self = this;

      $$quo.$$p = null;
      if ($eqeqeq($$$('Integer'), self)) {
        return $send2(self, $find_super(self, 'quo', $$quo, false, true), 'quo', [other], $yield)
      } else {
        return $rb_divide(self, other)
      }
    });
    
    $def(self, '$rationalize', function $$rationalize(eps) {
      var $a, $b, self = this, f = nil, n = nil;
      
      if (arguments.length > 1) {
        $Kernel.$raise($$$('ArgumentError'), "wrong number of arguments (" + (arguments.length) + " for 0..1)");
      }
      if ($eqeqeq($$$('Integer'), self)) {
        return $$$('Rational').$new(self, 1)
      } else if ($truthy(self['$infinite?']())) {
        return $Kernel.$raise($$$('FloatDomainError'), "Infinity")
      } else if ($truthy(self['$nan?']())) {
        return $Kernel.$raise($$$('FloatDomainError'), "NaN")
      } else if ($truthy(eps == null)) {
        
        $b = $$$('Math').$frexp(self), $a = $to_ary($b), (f = ($a[0] == null ? nil : $a[0])), (n = ($a[1] == null ? nil : $a[1]));
        f = $$$('Math').$ldexp(f, $$$($$$('Float'), 'MANT_DIG')).$to_i();
        n = $rb_minus(n, $$$($$$('Float'), 'MANT_DIG'));
        return $$$('Rational').$new($rb_times(2, f), (1)['$<<']($rb_minus(1, n))).$rationalize($$$('Rational').$new(1, (1)['$<<']($rb_minus(1, n))));
      } else {
        return self.$to_r().$rationalize(eps)
      }    }, -1);
    
    $def(self, '$remainder', function $$remainder(y) {
      var self = this;

      return $rb_minus(self, $rb_times(y, $rb_divide(self, y).$truncate()))
    });
    
    $def(self, '$round', function $$round(ndigits) {
      var $a, $b, self = this, exp = nil;
      if ($eqeqeq($$$('Integer'), self)) {
        
        if ($truthy(ndigits == null)) {
          return self
        }        if (($eqeqeq($$$('Float'), ndigits) && ($truthy(ndigits['$infinite?']())))) {
          $Kernel.$raise($$$('RangeError'), "Infinity");
        }        ndigits = $Opal['$coerce_to!'](ndigits, $$$('Integer'), "to_int");
        if ($truthy($rb_lt(ndigits, $$$($$$('Integer'), 'MIN')))) {
          $Kernel.$raise($$$('RangeError'), "out of bounds");
        }        if ($truthy(ndigits >= 0)) {
          return self
        }        ndigits = ndigits['$-@']();
        
        if (0.415241 * ndigits - 0.125 > self.$size()) {
          return 0;
        }

        var f = Math.pow(10, ndigits),
            x = Math.floor((Math.abs(self) + f / 2) / f) * f;

        return self < 0 ? -x : x;
      } else {
        
        if (($truthy(self['$nan?']()) && ($truthy(ndigits == null)))) {
          $Kernel.$raise($$$('FloatDomainError'), "NaN");
        }        ndigits = $Opal['$coerce_to!'](ndigits || 0, $$$('Integer'), "to_int");
        if ($truthy($rb_le(ndigits, 0))) {
          if ($truthy(self['$nan?']())) {
            $Kernel.$raise($$$('RangeError'), "NaN");
          } else if ($truthy(self['$infinite?']())) {
            $Kernel.$raise($$$('FloatDomainError'), "Infinity");
          }
        } else if ($eqeq(ndigits, 0)) {
          return Math.round(self)
        } else if (($truthy(self['$nan?']()) || ($truthy(self['$infinite?']())))) {
          return self
        }        $b = $$$('Math').$frexp(self), $a = $to_ary($b), (($a[0] == null ? nil : $a[0])), (exp = ($a[1] == null ? nil : $a[1]));
        if ($truthy($rb_ge(ndigits, $rb_minus($rb_plus($$$($$$('Float'), 'DIG'), 2), ($truthy($rb_gt(exp, 0)) ? ($rb_divide(exp, 4)) : ($rb_minus($rb_divide(exp, 3), 1))))))) {
          return self
        }        if ($truthy($rb_lt(ndigits, ($truthy($rb_gt(exp, 0)) ? ($rb_plus($rb_divide(exp, 3), 1)) : ($rb_divide(exp, 4)))['$-@']()))) {
          return 0
        }        return Math.round(self * Math.pow(10, ndigits)) / Math.pow(10, ndigits);      }    }, -1);
    
    $def(self, '$times', function $$times() {
      var block = $$times.$$p || nil, self = this;

      $$times.$$p = null;
      if (!$truthy(block)) {
        return $send(self, 'enum_for', ["times"], function $$33(){var self = $$33.$$s == null ? this : $$33.$$s;

          return self}, {$$s: self})
      }      
      for (var i = 0; i < self; i++) {
        block(i);
      }
      return self;
    });
    
    $def(self, '$to_f', $return_self);
    
    $def(self, '$to_i', function $$to_i() {
      var self = this;

      return self < 0 ? Math.ceil(self) : Math.floor(self);
    });
    
    $def(self, '$to_r', function $$to_r() {
      var $a, $b, self = this, f = nil, e = nil;

      if ($eqeqeq($$$('Integer'), self)) {
        return $$$('Rational').$new(self, 1)
      } else {
        
        $b = $$$('Math').$frexp(self), $a = $to_ary($b), (f = ($a[0] == null ? nil : $a[0])), (e = ($a[1] == null ? nil : $a[1]));
        f = $$$('Math').$ldexp(f, $$$($$$('Float'), 'MANT_DIG')).$to_i();
        e = $rb_minus(e, $$$($$$('Float'), 'MANT_DIG'));
        return $rb_times(f, $$$($$$('Float'), 'RADIX')['$**'](e)).$to_r();
      }
    });
    
    $def(self, '$to_s', function $$to_s(base) {
      var self = this;

      
      if (base == null) base = 10;
      base = $Opal['$coerce_to!'](base, $$$('Integer'), "to_int");
      if (($truthy($rb_lt(base, 2)) || ($truthy($rb_gt(base, 36))))) {
        $Kernel.$raise($$$('ArgumentError'), "invalid radix " + (base));
      }      if (($eqeq(self, 0) && ($truthy(1/self === -Infinity)))) {
        return "-0.0"
      }      return self.toString(base);    }, -1);
    
    $def(self, '$truncate', function $$truncate(ndigits) {
      var self = this;

      
      if (ndigits == null) ndigits = 0;
      
      var f = self.$to_f();

      if (f % 1 === 0 && ndigits >= 0) {
        return f;
      }

      var factor = Math.pow(10, ndigits),
          result = parseInt(f * factor, 10) / factor;

      if (f % 1 === 0) {
        result = Math.round(result);
      }

      return result;
    }, -1);
    
    $def(self, '$digits', function $$digits(base) {
      var self = this;

      
      if (base == null) base = 10;
      if ($rb_lt(self, 0)) {
        $Kernel.$raise($$$($$$('Math'), 'DomainError'), "out of domain");
      }      base = $Opal['$coerce_to!'](base, $$$('Integer'), "to_int");
      if ($truthy($rb_lt(base, 2))) {
        $Kernel.$raise($$$('ArgumentError'), "invalid radix " + (base));
      }      
      if (self != parseInt(self)) $Kernel.$raise($$$('NoMethodError'), "undefined method `digits' for " + (self.$inspect()));

      var value = self, result = [];

      if (self == 0) {
        return [0];
      }

      while (value != 0) {
        result.push(value % base);
        value = parseInt(value / base, 10);
      }

      return result;
    }, -1);
    
    $def(self, '$divmod', function $$divmod(other) {
      var $yield = $$divmod.$$p || nil, self = this;

      $$divmod.$$p = null;
      if (($truthy(self['$nan?']()) || ($truthy(other['$nan?']())))) {
        return $Kernel.$raise($$$('FloatDomainError'), "NaN")
      } else if ($truthy(self['$infinite?']())) {
        return $Kernel.$raise($$$('FloatDomainError'), "Infinity")
      } else {
        return $send2(self, $find_super(self, 'divmod', $$divmod, false, true), 'divmod', [other], $yield)
      }
    });
    
    $def(self, '$upto', function $$upto(stop) {
      var block = $$upto.$$p || nil, self = this;

      $$upto.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["upto", stop], function $$34(){var self = $$34.$$s == null ? this : $$34.$$s;

          
          if (!$eqeqeq($$$('Numeric'), stop)) {
            $Kernel.$raise($$$('ArgumentError'), "comparison of " + (self.$class()) + " with " + (stop.$class()) + " failed");
          }          if ($truthy($rb_lt(stop, self))) {
            return 0
          } else {
            return $rb_plus($rb_minus(stop, self), 1)
          }}, {$$s: self})
      }      
      if (!stop.$$is_number) {
        $Kernel.$raise($$$('ArgumentError'), "comparison of " + (self.$class()) + " with " + (stop.$class()) + " failed");
      }
      for (var i = self; i <= stop; i++) {
        block(i);
      }
      return self;
    });
    
    $def(self, '$zero?', function $Number_zero$ques$35() {
      var self = this;

      return self == 0;
    });
    
    $def(self, '$size', $return_val(4));
    
    $def(self, '$nan?', function $Number_nan$ques$36() {
      var self = this;

      return isNaN(self);
    });
    
    $def(self, '$finite?', function $Number_finite$ques$37() {
      var self = this;

      return self != Infinity && self != -Infinity && !isNaN(self);
    });
    
    $def(self, '$infinite?', function $Number_infinite$ques$38() {
      var self = this;

      
      if (self == Infinity) {
        return +1;
      }
      else if (self == -Infinity) {
        return -1;
      }
      else {
        return nil;
      }
    
    });
    
    $def(self, '$positive?', function $Number_positive$ques$39() {
      var self = this;

      return self != 0 && (self == Infinity || 1 / self > 0);
    });
    
    $def(self, '$negative?', function $Number_negative$ques$40() {
      var self = this;

      return self == -Infinity || 1 / self < 0;
    });
    
    function numberToUint8Array(num) {
      var uint8array = new Uint8Array(8);
      new DataView(uint8array.buffer).setFloat64(0, num, true);
      return uint8array;
    }

    function uint8ArrayToNumber(arr) {
      return new DataView(arr.buffer).getFloat64(0, true);
    }

    function incrementNumberBit(num) {
      var arr = numberToUint8Array(num);
      for (var i = 0; i < arr.length; i++) {
        if (arr[i] === 0xff) {
          arr[i] = 0;
        } else {
          arr[i]++;
          break;
        }
      }
      return uint8ArrayToNumber(arr);
    }

    function decrementNumberBit(num) {
      var arr = numberToUint8Array(num);
      for (var i = 0; i < arr.length; i++) {
        if (arr[i] === 0) {
          arr[i] = 0xff;
        } else {
          arr[i]--;
          break;
        }
      }
      return uint8ArrayToNumber(arr);
    }
    
    $def(self, '$next_float', function $$next_float() {
      var self = this;

      
      if ($eqeq(self, $$$($$$('Float'), 'INFINITY'))) {
        return $$$($$$('Float'), 'INFINITY')
      }      if ($truthy(self['$nan?']())) {
        return $$$($$$('Float'), 'NAN')
      }      if ($rb_ge(self, 0)) {
        return incrementNumberBit(Math.abs(self));
      } else {
        return decrementNumberBit(self);
      }    });
    
    $def(self, '$prev_float', function $$prev_float() {
      var self = this;

      
      if ($eqeq(self, $$$($$$('Float'), 'INFINITY')['$-@']())) {
        return $$$($$$('Float'), 'INFINITY')['$-@']()
      }      if ($truthy(self['$nan?']())) {
        return $$$($$$('Float'), 'NAN')
      }      if ($rb_gt(self, 0)) {
        return decrementNumberBit(self);
      } else {
        return -incrementNumberBit(Math.abs(self));
      }    });
    $alias(self, "arg", "angle");
    $alias(self, "eql?", "==");
    $alias(self, "fdiv", "/");
    $alias(self, "inspect", "to_s");
    $alias(self, "kind_of?", "is_a?");
    $alias(self, "magnitude", "abs");
    $alias(self, "modulo", "%");
    $alias(self, "object_id", "__id__");
    $alias(self, "phase", "angle");
    $alias(self, "succ", "next");
    return $alias(self, "to_int", "to_i");
  })('::', $$$('Numeric'));
  $const_set('::', 'Fixnum', $$$('Number'));
  (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Integer');

    var $nesting = [self].concat($parent_nesting);

    
    self.$$is_number_class = true;
    self.$$is_integer_class = true;
    (function(self, $parent_nesting) {
      var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting);

      
      
      $def(self, '$allocate', function $$allocate() {
        var self = this;

        return $Kernel.$raise($$$('TypeError'), "allocator undefined for " + (self.$name()))
      });
      
      Opal.udef(self, '$' + "new");      
      $def(self, '$sqrt', function $$sqrt(n) {
        
        
        n = $Opal['$coerce_to!'](n, $$$('Integer'), "to_int");
        
        if (n < 0) {
          $Kernel.$raise($$$($$$('Math'), 'DomainError'), "Numerical argument is out of domain - \"isqrt\"");
        }

        return parseInt(Math.sqrt(n), 10);
      });
      return $def(self, '$try_convert', function $$try_convert(object) {
        var self = this;

        return $$('Opal')['$coerce_to?'](object, self, "to_int")
      });
    })(Opal.get_singleton_class(self), $nesting);
    $const_set(self, 'MAX', Math.pow(2, 30) - 1);
    return $const_set(self, 'MIN', -Math.pow(2, 30));
  })('::', $$$('Numeric'), $nesting);
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Float');

    
    self.$$is_number_class = true;
    (function(self, $parent_nesting) {
      
      
      
      $def(self, '$allocate', function $$allocate() {
        var self = this;

        return $Kernel.$raise($$$('TypeError'), "allocator undefined for " + (self.$name()))
      });
      
      Opal.udef(self, '$' + "new");      return $def(self, '$===', function $eq_eq_eq$41(other) {
        
        return !!other.$$is_number;
      });
    })(Opal.get_singleton_class(self));
    $const_set(self, 'INFINITY', Infinity);
    $const_set(self, 'MAX', Number.MAX_VALUE);
    $const_set(self, 'MIN', Number.MIN_VALUE);
    $const_set(self, 'NAN', NaN);
    $const_set(self, 'DIG', 15);
    $const_set(self, 'MANT_DIG', 53);
    $const_set(self, 'RADIX', 2);
    return $const_set(self, 'EPSILON', Number.EPSILON || 2.2204460492503130808472633361816E-16);
  })('::', $$$('Numeric'));
};

Opal.modules["corelib/range"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $truthy = Opal.truthy, $Kernel = Opal.Kernel, $def = Opal.def, $not = Opal.not, $send2 = Opal.send2, $find_super = Opal.find_super, $rb_lt = Opal.rb_lt, $rb_le = Opal.rb_le, $send = Opal.send, $eqeq = Opal.eqeq, $eqeqeq = Opal.eqeqeq, $return_ivar = Opal.return_ivar, $rb_gt = Opal.rb_gt, $rb_minus = Opal.rb_minus, $Opal = Opal.Opal, $rb_divide = Opal.rb_divide, $rb_plus = Opal.rb_plus, $rb_times = Opal.rb_times, $rb_ge = Opal.rb_ge, $thrower = Opal.thrower, $alias = Opal.alias, self = Opal.top, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('require,include,attr_reader,raise,nil?,<=>,include?,!,<,<=,enum_for,size,upto,to_proc,respond_to?,class,succ,==,===,exclude_end?,eql?,begin,end,last,to_a,>,-@,-,to_i,coerce_to!,ceil,/,is_a?,new,loop,+,*,>=,each_with_index,%,step,bsearch,inspect,[],hash,cover?');
  
  self.$require("corelib/enumerable");
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Range');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting), $proto = self.$$prototype;

    $proto.begin = $proto.end = $proto.excl = nil;
    
    self.$include($$$('Enumerable'));
    self.$$prototype.$$is_range = true;
    self.$attr_reader("begin", "end");
    
    $def(self, '$initialize', function $$initialize(first, last, exclude) {
      var self = this;

      
      if (exclude == null) exclude = false;
      if ($truthy(self.begin)) {
        $Kernel.$raise($$$('NameError'), "'initialize' called twice");
      }      if (!(($truthy(first['$<=>'](last)) || ($truthy(first['$nil?']()))) || ($truthy(last['$nil?']())))) {
        $Kernel.$raise($$$('ArgumentError'), "bad value for range");
      }      self.begin = first;
      self.end = last;
      return (self.excl = exclude);
    }, -3);
    
    $def(self, '$===', function $Range_$eq_eq_eq$1(value) {
      var self = this;

      return self['$include?'](value)
    });
    
    function is_infinite(self) {
      if (self.begin === nil || self.end === nil ||
          self.begin === -Infinity || self.end === Infinity ||
          self.begin === Infinity || self.end === -Infinity) return true;
      return false;
    }
    
    $def(self, '$count', function $$count() {
      var block = $$count.$$p || nil, self = this;

      $$count.$$p = null;
      if (($not((block !== nil)) && ($truthy(is_infinite(self))))) {
        return $$$($$$('Float'), 'INFINITY')
      }      return $send2(self, $find_super(self, 'count', $$count, false, true), 'count', [], block);
    });
    
    $def(self, '$to_a', function $$to_a() {
      var $yield = $$to_a.$$p || nil, self = this;

      $$to_a.$$p = null;
      
      if ($truthy(is_infinite(self))) {
        $Kernel.$raise($$$('TypeError'), "cannot convert endless range to an array");
      }      return $send2(self, $find_super(self, 'to_a', $$to_a, false, true), 'to_a', [], $yield);
    });
    
    $def(self, '$cover?', function $Range_cover$ques$2(value) {
      var self = this, beg_cmp = nil, $ret_or_1 = nil, $ret_or_2 = nil, $ret_or_3 = nil, end_cmp = nil;

      
      beg_cmp = ($truthy(($ret_or_1 = ($truthy(($ret_or_2 = ($truthy(($ret_or_3 = self.begin['$nil?']())) ? (-1) : ($ret_or_3)))) ? ($ret_or_2) : (self.begin['$<=>'](value))))) && ($ret_or_1));
      end_cmp = ($truthy(($ret_or_1 = ($truthy(($ret_or_2 = ($truthy(($ret_or_3 = self.end['$nil?']())) ? (-1) : ($ret_or_3)))) ? ($ret_or_2) : (value['$<=>'](self.end))))) && ($ret_or_1));
      if ($truthy(($ret_or_1 = ($truthy(($ret_or_2 = ($truthy(self.excl) ? (($truthy(($ret_or_3 = end_cmp)) ? ($rb_lt(end_cmp, 0)) : ($ret_or_3))) : ($truthy(($ret_or_3 = end_cmp)) ? ($rb_le(end_cmp, 0)) : ($ret_or_3))))) ? (beg_cmp) : ($ret_or_2))))) {
        return $rb_le(beg_cmp, 0)
      } else {
        return $ret_or_1
      }    });
    
    $def(self, '$each', function $$each() {
      var block = $$each.$$p || nil, self = this, current = nil, last = nil, $ret_or_1 = nil;

      $$each.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["each"], function $$3(){var self = $$3.$$s == null ? this : $$3.$$s;

          return self.$size()}, {$$s: self})
      }      
      var i, limit;

      if (self.begin.$$is_number && self.end.$$is_number) {
        if (self.begin % 1 !== 0 || self.end % 1 !== 0) {
          $Kernel.$raise($$$('TypeError'), "can't iterate from Float");
        }

        for (i = self.begin, limit = self.end + ($truthy(self.excl) ? (0) : (1)); i < limit; i++) {
          block(i);
        }

        return self;
      }

      if (self.begin.$$is_string && self.end.$$is_string) {
        $send(self.begin, 'upto', [self.end, self.excl], block.$to_proc());
        return self;
      }
      current = self.begin;
      last = self.end;
      if (!$truthy(current['$respond_to?']("succ"))) {
        $Kernel.$raise($$$('TypeError'), "can't iterate from " + (current.$class()));
      }      while ($truthy(($truthy(($ret_or_1 = self.end['$nil?']())) ? ($ret_or_1) : ($rb_lt(current['$<=>'](last), 0))))) {
      
        Opal.yield1(block, current);
        current = current.$succ();
      }      if (($not(self.excl) && ($eqeq(current, last)))) {
        Opal.yield1(block, current);
      }      return self;
    });
    
    $def(self, '$eql?', function $Range_eql$ques$4(other) {
      var self = this, $ret_or_1 = nil, $ret_or_2 = nil;

      
      if (!$eqeqeq($$$('Range'), other)) {
        return false
      }      if ($truthy(($ret_or_1 = ($truthy(($ret_or_2 = self.excl['$==='](other['$exclude_end?']()))) ? (self.begin['$eql?'](other.$begin())) : ($ret_or_2))))) {
        return self.end['$eql?'](other.$end())
      } else {
        return $ret_or_1
      }    });
    
    $def(self, '$exclude_end?', $return_ivar("excl"));
    
    $def(self, '$first', function $$first(n) {
      var $yield = $$first.$$p || nil, self = this;

      $$first.$$p = null;
      if ($truthy(self.begin['$nil?']())) {
        $Kernel.$raise($$$('RangeError'), "cannot get the minimum of beginless range");
      }      if ($truthy(n == null)) {
        return self.begin
      }      return $send2(self, $find_super(self, 'first', $$first, false, true), 'first', [n], $yield);
    }, -1);
    
    $def(self, '$last', function $$last(n) {
      var self = this;
      if ($truthy(self.end['$nil?']())) {
        $Kernel.$raise($$$('RangeError'), "cannot get the maximum of endless range");
      }      if ($truthy(n == null)) {
        return self.end
      }      return self.$to_a().$last(n);
    }, -1);
    
    $def(self, '$max', function $$max() {
      var $yield = $$max.$$p || nil, self = this;

      $$max.$$p = null;
      if ($truthy(self.end['$nil?']())) {
        return $Kernel.$raise($$$('RangeError'), "cannot get the maximum of endless range")
      } else if (($yield !== nil)) {
        return $send2(self, $find_super(self, 'max', $$max, false, true), 'max', [], $yield)
      } else if (($not(self.begin['$nil?']()) && (($truthy($rb_gt(self.begin, self.end)) || (($truthy(self.excl) && ($eqeq(self.begin, self.end)))))))) {
        return nil
      } else {
        return self.excl ? self.end - 1 : self.end
      }
    });
    
    $def(self, '$min', function $$min() {
      var $yield = $$min.$$p || nil, self = this;

      $$min.$$p = null;
      if ($truthy(self.begin['$nil?']())) {
        return $Kernel.$raise($$$('RangeError'), "cannot get the minimum of beginless range")
      } else if (($yield !== nil)) {
        return $send2(self, $find_super(self, 'min', $$min, false, true), 'min', [], $yield)
      } else if (($not(self.end['$nil?']()) && (($truthy($rb_gt(self.begin, self.end)) || (($truthy(self.excl) && ($eqeq(self.begin, self.end)))))))) {
        return nil
      } else {
        return self.begin
      }
    });
    
    $def(self, '$size', function $$size() {
      var self = this, infinity = nil, range_begin = nil, range_end = nil;

      
      infinity = $$$($$$('Float'), 'INFINITY');
      if ((($eqeq(self.begin, infinity) && ($not(self.end['$nil?']()))) || (($eqeq(self.end, infinity['$-@']()) && ($not(self.begin['$nil?']())))))) {
        return 0
      }      if ($truthy(is_infinite(self))) {
        return infinity
      }      if (!($eqeqeq($$$('Numeric'), self.begin) && ($eqeqeq($$$('Numeric'), self.end)))) {
        return nil
      }      range_begin = self.begin;
      range_end = self.end;
      if ($truthy(self.excl)) {
        range_end = $rb_minus(range_end, 1);
      }      if ($truthy($rb_lt(range_end, range_begin))) {
        return 0
      }      return (Math.abs(range_end - range_begin) + 1).$to_i();
    });
    
    $def(self, '$step', function $$step(n) {
      var $yield = $$step.$$p || nil, self = this, $ret_or_1 = nil, i = nil;

      $$step.$$p = null;
      
      function coerceStepSize() {
        if (n == null) {
          n = 1;
        }
        else if (!n.$$is_number) {
          n = $Opal['$coerce_to!'](n, $$$('Integer'), "to_int");
        }

        if (n < 0) {
          $Kernel.$raise($$$('ArgumentError'), "step can't be negative");
        } else if (n === 0) {
          $Kernel.$raise($$$('ArgumentError'), "step can't be 0");
        }
      }

      function enumeratorSize() {
        if (!self.begin['$respond_to?']("succ")) {
          return nil;
        }

        if (self.begin.$$is_string && self.end.$$is_string) {
          return nil;
        }

        if (n % 1 === 0) {
          return $rb_divide(self.$size(), n).$ceil();
        } else {
          // n is a float
          var begin = self.begin, end = self.end,
              abs = Math.abs, floor = Math.floor,
              err = (abs(begin) + abs(end) + abs(end - begin)) / abs(n) * $$$($$$('Float'), 'EPSILON'),
              size;

          if (err > 0.5) {
            err = 0.5;
          }

          if (self.excl) {
            size = floor((end - begin) / n - err);
            if (size * n + begin < end) {
              size++;
            }
          } else {
            size = floor((end - begin) / n + err) + 1;
          }

          return size;
        }
      }
      if (!($yield !== nil)) {
        if (((($truthy(self.begin['$is_a?']($$('Numeric'))) || ($truthy(self.begin['$nil?']()))) && (($truthy(self.end['$is_a?']($$('Numeric'))) || ($truthy(self.end['$nil?']()))))) && ($not(($truthy(($ret_or_1 = self.begin['$nil?']())) ? (self.end['$nil?']()) : ($ret_or_1)))))) {
          return $$$($$$('Enumerator'), 'ArithmeticSequence').$new(self, n, "step")
        } else {
          return $send(self, 'enum_for', ["step", n], function $$5(){
            
            coerceStepSize();
            return enumeratorSize();
          })
        }
      }      coerceStepSize();
      if ($truthy(self.begin.$$is_number && self.end.$$is_number)) {
        
        i = 0;
        (function(){try { var $t_break = $thrower('break'); return $send(self, 'loop', [], function $$6(){var self = $$6.$$s == null ? this : $$6.$$s, current = nil;
          if (self.begin == null) self.begin = nil;
          if (self.excl == null) self.excl = nil;
          if (self.end == null) self.end = nil;

          
          current = $rb_plus(self.begin, $rb_times(i, n));
          if ($truthy(self.excl)) {
            if ($truthy($rb_ge(current, self.end))) {
              $t_break.$throw();
            }
          } else if ($truthy($rb_gt(current, self.end))) {
            $t_break.$throw();
          };
          Opal.yield1($yield, current);
          return (i = $rb_plus(i, 1));}, {$$s: self})} catch($e) {
          if ($e === $t_break) return $e.$v;
          throw $e;
        }})();
      } else {
        
        
        if (self.begin.$$is_string && self.end.$$is_string && n % 1 !== 0) {
          $Kernel.$raise($$$('TypeError'), "no implicit conversion to float from string");
        }
        $send(self, 'each_with_index', [], function $$7(value, idx){
          
          if (value == null) value = nil;
          if (idx == null) idx = nil;
          if ($eqeq(idx['$%'](n), 0)) {
            return Opal.yield1($yield, value);
          } else {
            return nil
          }});
      }      return self;
    }, -1);
    
    $def(self, '$%', function $Range_$percent$8(n) {
      var self = this;

      if (($truthy(self.begin['$is_a?']($$('Numeric'))) && ($truthy(self.end['$is_a?']($$('Numeric')))))) {
        return $$$($$$('Enumerator'), 'ArithmeticSequence').$new(self, n, "%")
      } else {
        return self.$step(n)
      }
    });
    
    $def(self, '$bsearch', function $$bsearch() {
      var block = $$bsearch.$$p || nil, self = this;

      $$bsearch.$$p = null;
      if (!(block !== nil)) {
        return self.$enum_for("bsearch")
      }      if ($truthy(is_infinite(self) && (self.begin.$$is_number || self.end.$$is_number))) {
        $Kernel.$raise($$$('NotImplementedError'), "Can't #bsearch an infinite range");
      }      if (!$truthy(self.begin.$$is_number && self.end.$$is_number)) {
        $Kernel.$raise($$$('TypeError'), "can't do binary search for " + (self.begin.$class()));
      }      return $send(self.$to_a(), 'bsearch', [], block.$to_proc());
    });
    
    $def(self, '$to_s', function $$to_s() {
      var self = this, $ret_or_1 = nil;

      return "" + (($truthy(($ret_or_1 = self.begin)) ? ($ret_or_1) : (""))) + (($truthy(self.excl) ? ("...") : (".."))) + (($truthy(($ret_or_1 = self.end)) ? ($ret_or_1) : ("")))
    });
    
    $def(self, '$inspect', function $$inspect() {
      var self = this, $ret_or_1 = nil;

      return "" + (($truthy(($ret_or_1 = self.begin)) ? (self.begin.$inspect()) : ($ret_or_1))) + (($truthy(self.excl) ? ("...") : (".."))) + (($truthy(($ret_or_1 = self.end)) ? (self.end.$inspect()) : ($ret_or_1)))
    });
    
    $def(self, '$marshal_load', function $$marshal_load(args) {
      var self = this;

      
      self.begin = args['$[]']("begin");
      self.end = args['$[]']("end");
      return (self.excl = args['$[]']("excl"));
    });
    
    $def(self, '$hash', function $$hash() {
      var self = this;

      return [self.begin, self.end, self.excl].$hash()
    });
    $alias(self, "==", "eql?");
    $alias(self, "include?", "cover?");
    return $alias(self, "member?", "cover?");
  })('::', null, $nesting);
};

Opal.modules["corelib/proc"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $slice = Opal.slice, $klass = Opal.klass, $truthy = Opal.truthy, $Kernel = Opal.Kernel, $defs = Opal.defs, $def = Opal.def, $send = Opal.send, $to_a = Opal.to_a, $return_self = Opal.return_self, $ensure_kwargs = Opal.ensure_kwargs, $Opal = Opal.Opal, $alias = Opal.alias, nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('raise,proc,call,to_proc,new,source_location,coerce_to!,dup');
  return (function($base, $super) {
    var self = $klass($base, $super, 'Proc');

    
    
    Opal.prop(self.$$prototype, '$$is_proc', true);
    Opal.prop(self.$$prototype, '$$is_lambda', false);
    $defs(self, '$new', function $Proc_new$1() {
      var block = $Proc_new$1.$$p || nil;

      $Proc_new$1.$$p = null;
      if (!$truthy(block)) {
        $Kernel.$raise($$$('ArgumentError'), "tried to create a Proc object without a block");
      }      return block;
    });
    
    $def(self, '$call', function $$call($a) {
      var block = $$call.$$p || nil, $post_args, args, self = this;

      $$call.$$p = null;
      $post_args = $slice(arguments);
      args = $post_args;
      
      if (block !== nil) {
        self.$$p = block;
      }

      var result, $brk = self.$$brk, $ret = self.$$ret;

      if ($brk || ($ret && self.$$is_lambda)) {
        try {
          if (self.$$is_lambda) {
            result = self.apply(null, args);
          }
          else {
            result = Opal.yieldX(self, args);
          }
        } catch (err) {
          if (err === $brk) {
            return err.$v;
          }
          else if (self.$$is_lambda && err === $ret) {
            return err.$v;
          }
          else {
            throw err;
          }
        }
      }
      else {
        if (self.$$is_lambda) {
          result = self.apply(null, args);
        }
        else {
          result = Opal.yieldX(self, args);
        }
      }

      return result;
    }, -1);
    
    $def(self, '$>>', function $Proc_$gt$gt$2(other) {
      var self = this;

      $Proc_$gt$gt$2.$$p = null;
      return $send($Kernel, 'proc', [], function $$3($a){var block = $$3.$$p || nil, $post_args, args, self = $$3.$$s == null ? this : $$3.$$s, out = nil;

        $$3.$$p = null;
        $post_args = $slice(arguments);
        args = $post_args;
        out = $send(self, 'call', $to_a(args), block.$to_proc());
        return other.$call(out);}, {$$arity: -1, $$s: self})
    });
    
    $def(self, '$<<', function $Proc_$lt$lt$4(other) {
      var self = this;

      $Proc_$lt$lt$4.$$p = null;
      return $send($Kernel, 'proc', [], function $$5($a){var block = $$5.$$p || nil, $post_args, args, self = $$5.$$s == null ? this : $$5.$$s, out = nil;

        $$5.$$p = null;
        $post_args = $slice(arguments);
        args = $post_args;
        out = $send(other, 'call', $to_a(args), block.$to_proc());
        return self.$call(out);}, {$$arity: -1, $$s: self})
    });
    
    $def(self, '$to_proc', $return_self);
    
    $def(self, '$lambda?', function $Proc_lambda$ques$6() {
      var self = this;

      return !!self.$$is_lambda;
    });
    
    $def(self, '$arity', function $$arity() {
      var self = this;

      
      if (self.$$is_curried) {
        return -1;
      } else if (self.$$arity != null) {
        return self.$$arity;
      } else {
        return self.length;
      }
    
    });
    
    $def(self, '$source_location', function $$source_location() {
      var self = this, $ret_or_1 = nil;

      
      if (self.$$is_curried) { return nil; }      if ($truthy(($ret_or_1 = self.$$source_location))) {
        return $ret_or_1
      } else {
        return nil
      }    });
    
    $def(self, '$binding', function $$binding() {
      var self = this;

      
      if (self.$$is_curried) { $Kernel.$raise($$$('ArgumentError'), "Can't create Binding"); }      if ($truthy((($$$('::', 'Binding', 'skip_raise')) ? 'constant' : nil))) {
        return $$$('Binding').$new(nil, [], self.$$s, self.$source_location())
      } else {
        return nil
      }    });
    
    $def(self, '$parameters', function $$parameters($kwargs) {
      var lambda, self = this;

      
      $kwargs = $ensure_kwargs($kwargs);
      
      lambda = $kwargs.$$smap["lambda"];      
      if (self.$$is_curried) {
        return [["rest"]];
      } else if (self.$$parameters) {
        if (lambda == null ? self.$$is_lambda : lambda) {
          return self.$$parameters;
        } else {
          var result = [], i, length;

          for (i = 0, length = self.$$parameters.length; i < length; i++) {
            var parameter = self.$$parameters[i];

            if (parameter[0] === 'req') {
              // required arguments always have name
              parameter = ['opt', parameter[1]];
            }

            result.push(parameter);
          }

          return result;
        }
      } else {
        return [];
      }
    }, -1);
    
    $def(self, '$curry', function $$curry(arity) {
      var self = this;
      
      if (arity === undefined) {
        arity = self.length;
      }
      else {
        arity = $Opal['$coerce_to!'](arity, $$$('Integer'), "to_int");
        if (self.$$is_lambda && arity !== self.length) {
          $Kernel.$raise($$$('ArgumentError'), "wrong number of arguments (" + (arity) + " for " + (self.length) + ")");
        }
      }

      function curried () {
        var args = $slice(arguments),
            length = args.length,
            result;

        if (length > arity && self.$$is_lambda && !self.$$is_curried) {
          $Kernel.$raise($$$('ArgumentError'), "wrong number of arguments (" + (length) + " for " + (arity) + ")");
        }

        if (length >= arity) {
          return self.$call.apply(self, args);
        }

        result = function () {
          return curried.apply(null,
            args.concat($slice(arguments)));
        };
        result.$$is_lambda = self.$$is_lambda;
        result.$$is_curried = true;

        return result;
      }
      curried.$$is_lambda = self.$$is_lambda;
      curried.$$is_curried = true;
      return curried;
    }, -1);
    
    $def(self, '$dup', function $$dup() {
      var self = this;

      
      var original_proc = self.$$original_proc || self,
          proc = function () {
            return original_proc.apply(this, arguments);
          };

      for (var prop in self) {
        if (self.hasOwnProperty(prop)) {
          proc[prop] = self[prop];
        }
      }

      return proc;
    
    });
    $alias(self, "===", "call");
    $alias(self, "clone", "dup");
    $alias(self, "yield", "call");
    return $alias(self, "[]", "call");
  })('::', Function)
};

Opal.modules["corelib/method"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $def = Opal.def, $truthy = Opal.truthy, $slice = Opal.slice, $alias = Opal.alias, $Kernel = Opal.Kernel, $send = Opal.send, $to_a = Opal.to_a, nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('attr_reader,arity,curry,>>,<<,new,class,join,source_location,call,raise,bind,to_proc');
  
  (function($base, $super) {
    var self = $klass($base, $super, 'Method');

    var $proto = self.$$prototype;

    $proto.method = $proto.receiver = $proto.owner = $proto.name = nil;
    
    self.$attr_reader("owner", "receiver", "name");
    
    $def(self, '$initialize', function $$initialize(receiver, owner, method, name) {
      var self = this;

      
      self.receiver = receiver;
      self.owner = owner;
      self.name = name;
      return (self.method = method);
    });
    
    $def(self, '$arity', function $$arity() {
      var self = this;

      return self.method.$arity()
    });
    
    $def(self, '$parameters', function $$parameters() {
      var self = this;

      return self.method.$$parameters
    });
    
    $def(self, '$source_location', function $$source_location() {
      var self = this, $ret_or_1 = nil;

      if ($truthy(($ret_or_1 = self.method.$$source_location))) {
        return $ret_or_1
      } else {
        return ["(eval)", 0]
      }
    });
    
    $def(self, '$comments', function $$comments() {
      var self = this, $ret_or_1 = nil;

      if ($truthy(($ret_or_1 = self.method.$$comments))) {
        return $ret_or_1
      } else {
        return []
      }
    });
    
    $def(self, '$call', function $$call($a) {
      var block = $$call.$$p || nil, $post_args, args, self = this;

      $$call.$$p = null;
      $post_args = $slice(arguments);
      args = $post_args;
      
      self.method.$$p = block;

      return self.method.apply(self.receiver, args);
    }, -1);
    
    $def(self, '$curry', function $$curry(arity) {
      var self = this;
      return self.method.$curry(arity);
    }, -1);
    
    $def(self, '$>>', function $Method_$gt$gt$1(other) {
      var self = this;

      return self.method['$>>'](other)
    });
    
    $def(self, '$<<', function $Method_$lt$lt$2(other) {
      var self = this;

      return self.method['$<<'](other)
    });
    
    $def(self, '$unbind', function $$unbind() {
      var self = this;

      return $$$('UnboundMethod').$new(self.receiver.$class(), self.owner, self.method, self.name)
    });
    
    $def(self, '$to_proc', function $$to_proc() {
      var self = this;

      
      var proc = self.$call.bind(self);
      proc.$$unbound = self.method;
      proc.$$is_lambda = true;
      proc.$$arity = self.method.$$arity == null ? self.method.length : self.method.$$arity;
      proc.$$parameters = self.method.$$parameters;
      return proc;
    
    });
    
    $def(self, '$inspect', function $$inspect() {
      var self = this;

      return "#<" + (self.$class()) + ": " + (self.receiver.$class()) + "#" + (self.name) + " (defined in " + (self.owner) + " in " + (self.$source_location().$join(":")) + ")>"
    });
    $alias(self, "[]", "call");
    return $alias(self, "===", "call");
  })('::', null);
  return (function($base, $super) {
    var self = $klass($base, $super, 'UnboundMethod');

    var $proto = self.$$prototype;

    $proto.method = $proto.owner = $proto.name = $proto.source = nil;
    
    self.$attr_reader("source", "owner", "name");
    
    $def(self, '$initialize', function $$initialize(source, owner, method, name) {
      var self = this;

      
      self.source = source;
      self.owner = owner;
      self.method = method;
      return (self.name = name);
    });
    
    $def(self, '$arity', function $$arity() {
      var self = this;

      return self.method.$arity()
    });
    
    $def(self, '$parameters', function $$parameters() {
      var self = this;

      return self.method.$$parameters
    });
    
    $def(self, '$source_location', function $$source_location() {
      var self = this, $ret_or_1 = nil;

      if ($truthy(($ret_or_1 = self.method.$$source_location))) {
        return $ret_or_1
      } else {
        return ["(eval)", 0]
      }
    });
    
    $def(self, '$comments', function $$comments() {
      var self = this, $ret_or_1 = nil;

      if ($truthy(($ret_or_1 = self.method.$$comments))) {
        return $ret_or_1
      } else {
        return []
      }
    });
    
    $def(self, '$bind', function $$bind(object) {
      var self = this;

      
      if (self.owner.$$is_module || Opal.is_a(object, self.owner)) {
        return $$$('Method').$new(object, self.owner, self.method, self.name);
      }
      else {
        $Kernel.$raise($$$('TypeError'), "can't bind singleton method to a different class (expected " + (object) + ".kind_of?(" + (self.owner) + " to be true)");
      }
    
    });
    
    $def(self, '$bind_call', function $$bind_call(object, $a) {
      var block = $$bind_call.$$p || nil, $post_args, args, self = this;

      $$bind_call.$$p = null;
      $post_args = $slice(arguments, 1);
      args = $post_args;
      return $send(self.$bind(object), 'call', $to_a(args), block.$to_proc());
    }, -2);
    return $def(self, '$inspect', function $$inspect() {
      var self = this;

      return "#<" + (self.$class()) + ": " + (self.source) + "#" + (self.name) + " (defined in " + (self.owner) + " in " + (self.$source_location().$join(":")) + ")>"
    });
  })('::', null);
};

Opal.modules["corelib/variables"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $gvars = Opal.gvars, $const_set = Opal.const_set, $Object = Opal.Object, $hash2 = Opal.hash2, nil = Opal.nil;

  Opal.add_stubs('new');
  
  $gvars['&'] = $gvars['~'] = $gvars['`'] = $gvars["'"] = nil;
  $gvars.LOADED_FEATURES = ($gvars["\""] = Opal.loaded_features);
  $gvars.LOAD_PATH = ($gvars[":"] = []);
  $gvars["/"] = "\n";
  $gvars[","] = nil;
  $const_set('::', 'ARGV', []);
  $const_set('::', 'ARGF', $Object.$new());
  $const_set('::', 'ENV', $hash2([], {}));
  $gvars.VERBOSE = false;
  $gvars.DEBUG = false;
  return ($gvars.SAFE = 0);
};

Opal.modules["corelib/io"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $const_set = Opal.const_set, $not = Opal.not, $truthy = Opal.truthy, $def = Opal.def, $return_ivar = Opal.return_ivar, $return_val = Opal.return_val, $slice = Opal.slice, $Kernel = Opal.Kernel, $gvars = Opal.gvars, $send = Opal.send, $to_a = Opal.to_a, $rb_plus = Opal.rb_plus, $neqeq = Opal.neqeq, $range = Opal.range, $hash2 = Opal.hash2, $eqeq = Opal.eqeq, $to_ary = Opal.to_ary, $rb_gt = Opal.rb_gt, $assign_ivar_val = Opal.assign_ivar_val, $alias = Opal.alias, $a, nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('attr_reader,attr_accessor,!,match?,include?,size,write,String,flatten,puts,sysread_noraise,+,!=,[],ord,getc,readchar,raise,gets,==,to_str,length,split,sub,sysread,>,to_a,each_line,enum_for,getbyte,closed_write?,closed_read?,each,eof,new,write_proc=,read_proc=');
  
  (function($base, $super) {
    var self = $klass($base, $super, 'IO');

    var $proto = self.$$prototype;

    $proto.read_buffer = $proto.closed = nil;
    
    $const_set(self, 'SEEK_SET', 0);
    $const_set(self, 'SEEK_CUR', 1);
    $const_set(self, 'SEEK_END', 2);
    $const_set(self, 'SEEK_DATA', 3);
    $const_set(self, 'SEEK_HOLE', 4);
    $const_set(self, 'READABLE', 1);
    $const_set(self, 'WRITABLE', 4);
    self.$attr_reader("eof");
    self.$attr_accessor("read_proc", "sync", "tty", "write_proc");
    
    $def(self, '$initialize', function $$initialize(fd, flags) {
      var self = this;

      
      if (flags == null) flags = "r";
      self.fd = fd;
      self.flags = flags;
      self.eof = false;
      if (($truthy(flags['$include?']("r")) && ($not(flags['$match?'](/[wa+]/))))) {
        return (self.closed = "write")
      } else if (($truthy(flags['$match?'](/[wa]/)) && ($not(flags['$match?'](/[r+]/))))) {
        return (self.closed = "read")
      } else {
        return nil
      }    }, -2);
    
    $def(self, '$fileno', $return_ivar("fd"));
    
    $def(self, '$tty?', function $IO_tty$ques$1() {
      var self = this;

      return self.tty == true;
    });
    
    $def(self, '$write', function $$write(string) {
      var self = this;

      
      self.write_proc(string);
      return string.$size();
    });
    
    $def(self, '$flush', $return_val(nil));
    
    $def(self, '$<<', function $IO_$lt$lt$2(string) {
      var self = this;

      
      self.$write(string);
      return self;
    });
    
    $def(self, '$print', function $$print($a) {
      var $post_args, args, self = this;
      if ($gvars[","] == null) $gvars[","] = nil;

      
      $post_args = $slice(arguments);
      args = $post_args;
      
      for (var i = 0, ii = args.length; i < ii; i++) {
        args[i] = $Kernel.$String(args[i]);
      }
      self.$write(args.join($gvars[","]));
      return nil;
    }, -1);
    
    $def(self, '$puts', function $$puts($a) {
      var $post_args, args, self = this;

      
      $post_args = $slice(arguments);
      args = $post_args;
      
      var line;
      if (args.length === 0) {
        self.$write("\n");
        return nil;
      } else {
        for (var i = 0, ii = args.length; i < ii; i++) {
          if (args[i].$$is_array){
            var ary = (args[i]).$flatten();
            if (ary.length > 0) $send(self, 'puts', $to_a((ary)));
          } else {
            if (args[i].$$is_string) {
              line = args[i].valueOf();
            } else {
              line = $Kernel.$String(args[i]);
            }
            if (!line.endsWith("\n")) line += "\n";
            self.$write(line);
          }
        }
      }
      return nil;
    }, -1);
    
    $def(self, '$getc', function $$getc() {
      var self = this, $ret_or_1 = nil, parts = nil, ret = nil;

      
      self.read_buffer = ($truthy(($ret_or_1 = self.read_buffer)) ? ($ret_or_1) : (""));
      parts = "";
      do {
      
        self.read_buffer = $rb_plus(self.read_buffer, parts);
        if ($neqeq(self.read_buffer, "")) {
          
          ret = self.read_buffer['$[]'](0);
          self.read_buffer = self.read_buffer['$[]']($range(1, -1, false));
          return ret;
        }      } while ($truthy((parts = self.$sysread_noraise(1))));      return nil;
    });
    
    $def(self, '$getbyte', function $$getbyte() {
      var $a, self = this;

      return ($a = self.$getc(), ($a === nil || $a == null) ? nil : $a.$ord())
    });
    
    $def(self, '$readbyte', function $$readbyte() {
      var self = this;

      return self.$readchar().$ord()
    });
    
    $def(self, '$readchar', function $$readchar() {
      var self = this, $ret_or_1 = nil;

      if ($truthy(($ret_or_1 = self.$getc()))) {
        return $ret_or_1
      } else {
        return $Kernel.$raise($$$('EOFError'), "end of file reached")
      }
    });
    
    $def(self, '$readline', function $$readline($a) {
      var $post_args, args, self = this, $ret_or_1 = nil;

      
      $post_args = $slice(arguments);
      args = $post_args;
      if ($truthy(($ret_or_1 = $send(self, 'gets', $to_a(args))))) {
        return $ret_or_1
      } else {
        return $Kernel.$raise($$$('EOFError'), "end of file reached")
      }    }, -1);
    
    $def(self, '$gets', function $$gets(sep, limit, opts) {
      var $a, $b, self = this, orig_sep = nil, $ret_or_1 = nil, seplen = nil, data = nil, ret = nil, orig_buffer = nil;
      if ($gvars["/"] == null) $gvars["/"] = nil;

      
      if (sep == null) sep = false;
      if (limit == null) limit = nil;
      if (opts == null) opts = $hash2([], {});
      if (($truthy(sep.$$is_number) && ($not(limit)))) {
        $a = [false, sep, limit], (sep = $a[0]), (limit = $a[1]), (opts = $a[2]);
      }      if ((($truthy(sep.$$is_hash) && ($not(limit))) && ($eqeq(opts, $hash2([], {}))))) {
        $a = [false, nil, sep], (sep = $a[0]), (limit = $a[1]), (opts = $a[2]);
      } else if (($truthy(limit.$$is_hash) && ($eqeq(opts, $hash2([], {}))))) {
        $a = [sep, nil, limit], (sep = $a[0]), (limit = $a[1]), (opts = $a[2]);
      }      orig_sep = sep;
      if ($eqeq(sep, false)) {
        sep = $gvars["/"];
      }      if ($eqeq(sep, "")) {
        sep = /\r?\n\r?\n/;
      }      sep = ($truthy(($ret_or_1 = sep)) ? ($ret_or_1) : (""));
      if (!$eqeq(orig_sep, "")) {
        sep = sep.$to_str();
      }      seplen = ($eqeq(orig_sep, "") ? (2) : (sep.$length()));
      if ($eqeq(sep, " ")) {
        sep = / /;
      }      self.read_buffer = ($truthy(($ret_or_1 = self.read_buffer)) ? ($ret_or_1) : (""));
      data = "";
      ret = nil;
      do {
      
        self.read_buffer = $rb_plus(self.read_buffer, data);
        if (($neqeq(sep, "") && ($truthy(($truthy(sep.$$is_regexp) ? (self.read_buffer['$match?'](sep)) : (self.read_buffer['$include?'](sep))))))) {
          
          orig_buffer = self.read_buffer;
          $b = self.read_buffer.$split(sep, 2), $a = $to_ary($b), (ret = ($a[0] == null ? nil : $a[0])), (self.read_buffer = ($a[1] == null ? nil : $a[1]));
          if ($neqeq(ret, orig_buffer)) {
            ret = $rb_plus(ret, orig_buffer['$[]'](ret.$length(), seplen));
          }          break;
        }      } while ($truthy((data = self.$sysread_noraise(($eqeq(sep, "") ? (65536) : (1))))));      if (!$truthy(ret)) {
        
        $a = [($truthy(($ret_or_1 = self.read_buffer)) ? ($ret_or_1) : ("")), ""], (ret = $a[0]), (self.read_buffer = $a[1]);
        if ($eqeq(ret, "")) {
          ret = nil;
        }      }      if ($truthy(ret)) {
        
        if ($truthy(limit)) {
          
          ret = ret['$[]'](Opal.Range.$new(0,limit, true));
          self.read_buffer = $rb_plus(ret['$[]'](Opal.Range.$new(limit, -1, false)), self.read_buffer);
        }        if ($truthy(opts['$[]']("chomp"))) {
          ret = ret.$sub(/\r?\n$/, "");
        }        if ($eqeq(orig_sep, "")) {
          ret = ret.$sub(/^[\r\n]+/, "");
        }      }      if ($eqeq(orig_sep, false)) {
        $gvars._ = ret;
      }      return ret;
    }, -1);
    
    $def(self, '$sysread', function $$sysread(integer) {
      var self = this, $ret_or_1 = nil;

      if ($truthy(($ret_or_1 = self.read_proc(integer)))) {
        return $ret_or_1
      } else {
        
        self.eof = true;
        return $Kernel.$raise($$$('EOFError'), "end of file reached");
      }
    });
    
    $def(self, '$sysread_noraise', function $$sysread_noraise(integer) {
      var self = this;

      try {
        return self.$sysread(integer)
      } catch ($err) {
        if (Opal.rescue($err, [$$$('EOFError')])) {
          try {
            return nil
          } finally { Opal.pop_exception(); }
        } else { throw $err; }
      }
    });
    
    $def(self, '$readpartial', function $$readpartial(integer) {
      var $a, self = this, $ret_or_1 = nil, part = nil, ret = nil;

      
      self.read_buffer = ($truthy(($ret_or_1 = self.read_buffer)) ? ($ret_or_1) : (""));
      part = self.$sysread(integer);
      $a = [$rb_plus(self.read_buffer, ($truthy(($ret_or_1 = part)) ? ($ret_or_1) : (""))), ""], (ret = $a[0]), (self.read_buffer = $a[1]);
      if ($eqeq(ret, "")) {
        ret = nil;
      }      return ret;
    });
    
    $def(self, '$read', function $$read(integer) {
      var $a, self = this, $ret_or_1 = nil, parts = nil, ret = nil;

      
      if (integer == null) integer = nil;
      self.read_buffer = ($truthy(($ret_or_1 = self.read_buffer)) ? ($ret_or_1) : (""));
      parts = "";
      ret = nil;
      do {
      
        self.read_buffer = $rb_plus(self.read_buffer, parts);
        if (($truthy(integer) && ($truthy($rb_gt(self.read_buffer.$length(), integer))))) {
          
          $a = [self.read_buffer['$[]'](Opal.Range.$new(0,integer, true)), self.read_buffer['$[]'](Opal.Range.$new(integer, -1, false))], (ret = $a[0]), (self.read_buffer = $a[1]);
          return ret;
        }      } while ($truthy((parts = self.$sysread_noraise(($truthy(($ret_or_1 = integer)) ? ($ret_or_1) : (65536))))));      $a = [self.read_buffer, ""], (ret = $a[0]), (self.read_buffer = $a[1]);
      return ret;
    }, -1);
    
    $def(self, '$readlines', function $$readlines(separator) {
      var self = this;
      if ($gvars["/"] == null) $gvars["/"] = nil;

      
      if (separator == null) separator = $gvars["/"];
      return self.$each_line(separator).$to_a();
    }, -1);
    
    $def(self, '$each', function $$each($a, $b) {
      var block = $$each.$$p || nil, $post_args, sep, args, self = this, s = nil;
      if ($gvars["/"] == null) $gvars["/"] = nil;

      $$each.$$p = null;
      $post_args = $slice(arguments);
      
      if ($post_args.length > 0) sep = $post_args.shift();if (sep == null) sep = $gvars["/"];
      args = $post_args;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["each", sep].concat($to_a(args)))
      }      while ($truthy((s = $send(self, 'gets', [sep].concat($to_a(args)))))) {
      Opal.yield1(block, s);
      }      return self;
    }, -1);
    
    $def(self, '$each_byte', function $$each_byte() {
      var block = $$each_byte.$$p || nil, self = this, s = nil;

      $$each_byte.$$p = null;
      if (!(block !== nil)) {
        return self.$enum_for("each_byte")
      }      while ($truthy((s = self.$getbyte()))) {
      Opal.yield1(block, s);
      }      return self;
    });
    
    $def(self, '$each_char', function $$each_char() {
      var block = $$each_char.$$p || nil, self = this, s = nil;

      $$each_char.$$p = null;
      if (!(block !== nil)) {
        return self.$enum_for("each_char")
      }      while ($truthy((s = self.$getc()))) {
      Opal.yield1(block, s);
      }      return self;
    });
    
    $def(self, '$close', $assign_ivar_val("closed", "both"));
    
    $def(self, '$close_read', function $$close_read() {
      var self = this;

      if ($eqeq(self.closed, "write")) {
        return (self.closed = "both")
      } else {
        return (self.closed = "read")
      }
    });
    
    $def(self, '$close_write', function $$close_write() {
      var self = this;

      if ($eqeq(self.closed, "read")) {
        return (self.closed = "both")
      } else {
        return (self.closed = "write")
      }
    });
    
    $def(self, '$closed?', function $IO_closed$ques$3() {
      var self = this;

      return self.closed['$==']("both")
    });
    
    $def(self, '$closed_read?', function $IO_closed_read$ques$4() {
      var self = this, $ret_or_1 = nil;

      if ($truthy(($ret_or_1 = self.closed['$==']("read")))) {
        return $ret_or_1
      } else {
        return self.closed['$==']("both")
      }
    });
    
    $def(self, '$closed_write?', function $IO_closed_write$ques$5() {
      var self = this, $ret_or_1 = nil;

      if ($truthy(($ret_or_1 = self.closed['$==']("write")))) {
        return $ret_or_1
      } else {
        return self.closed['$==']("both")
      }
    });
    
    $def(self, '$check_writable', function $$check_writable() {
      var self = this;

      if ($truthy(self['$closed_write?']())) {
        return $Kernel.$raise($$$('IOError'), "not opened for writing")
      } else {
        return nil
      }
    });
    
    $def(self, '$check_readable', function $$check_readable() {
      var self = this;

      if ($truthy(self['$closed_read?']())) {
        return $Kernel.$raise($$$('IOError'), "not opened for reading")
      } else {
        return nil
      }
    });
    $alias(self, "each_line", "each");
    return $alias(self, "eof?", "eof");
  })('::', null);
  $const_set('::', 'STDIN', ($gvars.stdin = $$$('IO').$new(0, "r")));
  $const_set('::', 'STDOUT', ($gvars.stdout = $$$('IO').$new(1, "w")));
  $const_set('::', 'STDERR', ($gvars.stderr = $$$('IO').$new(2, "w")));
  var console = Opal.global.console;
  $$$('STDOUT')['$write_proc='](typeof(process) === 'object' && typeof(process.stdout) === 'object' ? function(s){process.stdout.write(s);} : function(s){console.log(s);});
  $$$('STDERR')['$write_proc='](typeof(process) === 'object' && typeof(process.stderr) === 'object' ? function(s){process.stderr.write(s);} : function(s){console.warn(s);});
  return ($a = [function(s) { var p = prompt(); if (p !== null) return p + "\n"; return nil; }], $send($$$('STDIN'), 'read_proc=', $a), $a[$a.length - 1]);
};

Opal.modules["opal/regexp_anchors"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $module = Opal.module, $const_set = Opal.const_set, $nesting = []; Opal.nil; var $$$ = Opal.$$$;

  Opal.add_stubs('new');
  return (function($base, $parent_nesting) {
    var self = $module($base, 'Opal');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting);

    
    $const_set(self, 'REGEXP_START', "^");
    $const_set(self, 'REGEXP_END', "$");
    $const_set(self, 'FORBIDDEN_STARTING_IDENTIFIER_CHARS', "\\u0001-\\u002F\\u003A-\\u0040\\u005B-\\u005E\\u0060\\u007B-\\u007F");
    $const_set(self, 'FORBIDDEN_ENDING_IDENTIFIER_CHARS', "\\u0001-\\u0020\\u0022-\\u002F\\u003A-\\u003E\\u0040\\u005B-\\u005E\\u0060\\u007B-\\u007F");
    $const_set(self, 'INLINE_IDENTIFIER_REGEXP', $$('Regexp').$new("[^" + ($$$(self, 'FORBIDDEN_STARTING_IDENTIFIER_CHARS')) + "]*[^" + ($$$(self, 'FORBIDDEN_ENDING_IDENTIFIER_CHARS')) + "]"));
    $const_set(self, 'FORBIDDEN_CONST_NAME_CHARS', "\\u0001-\\u0020\\u0021-\\u002F\\u003B-\\u003F\\u0040\\u005B-\\u005E\\u0060\\u007B-\\u007F");
    return $const_set(self, 'CONST_NAME_REGEXP', $$('Regexp').$new("" + ($$$(self, 'REGEXP_START')) + "(::)?[A-Z][^" + ($$$(self, 'FORBIDDEN_CONST_NAME_CHARS')) + "]*" + ($$$(self, 'REGEXP_END'))));
  })($nesting[0], $nesting)
};

Opal.modules["opal/mini"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $Object = Opal.Object; Opal.nil;

  Opal.add_stubs('require');
  
  $Object.$require("opal/base");
  $Object.$require("corelib/nil");
  $Object.$require("corelib/boolean");
  $Object.$require("corelib/string");
  $Object.$require("corelib/comparable");
  $Object.$require("corelib/enumerable");
  $Object.$require("corelib/enumerator");
  $Object.$require("corelib/array");
  $Object.$require("corelib/hash");
  $Object.$require("corelib/number");
  $Object.$require("corelib/range");
  $Object.$require("corelib/proc");
  $Object.$require("corelib/method");
  $Object.$require("corelib/regexp");
  $Object.$require("corelib/variables");
  $Object.$require("corelib/io");
  return $Object.$require("opal/regexp_anchors");
};

Opal.modules["corelib/kernel/format"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $coerce_to = Opal.coerce_to, $module = Opal.module, $slice = Opal.slice, $truthy = Opal.truthy, $eqeq = Opal.eqeq, $Opal = Opal.Opal, $Kernel = Opal.Kernel, $gvars = Opal.gvars, $def = Opal.def, $alias = Opal.alias, nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('respond_to?,[],==,length,coerce_to?,nil?,to_a,raise,to_int,fetch,Integer,Float,to_ary,to_str,inspect,to_s,format');
  return (function($base) {
    var self = $module($base, 'Kernel');

    
    
    
    $def(self, '$format', function $$format(format_string, $a) {
      var $post_args, args, ary = nil;
      if ($gvars.DEBUG == null) $gvars.DEBUG = nil;

      
      $post_args = $slice(arguments, 1);
      args = $post_args;
      if (($eqeq(args.$length(), 1) && ($truthy(args['$[]'](0)['$respond_to?']("to_ary"))))) {
        
        ary = $Opal['$coerce_to?'](args['$[]'](0), $$$('Array'), "to_ary");
        if (!$truthy(ary['$nil?']())) {
          args = ary.$to_a();
        }      }      
      var result = '',
          //used for slicing:
          begin_slice = 0,
          end_slice,
          //used for iterating over the format string:
          i,
          len = format_string.length,
          //used for processing field values:
          arg,
          str,
          //used for processing %g and %G fields:
          exponent,
          //used for keeping track of width and precision:
          width,
          precision,
          //used for holding temporary values:
          tmp_num,
          //used for processing %{} and %<> fileds:
          hash_parameter_key,
          closing_brace_char,
          //used for processing %b, %B, %o, %x, and %X fields:
          base_number,
          base_prefix,
          base_neg_zero_regex,
          base_neg_zero_digit,
          //used for processing arguments:
          next_arg,
          seq_arg_num = 1,
          pos_arg_num = 0,
          //used for keeping track of flags:
          flags,
          FNONE  = 0,
          FSHARP = 1,
          FMINUS = 2,
          FPLUS  = 4,
          FZERO  = 8,
          FSPACE = 16,
          FWIDTH = 32,
          FPREC  = 64,
          FPREC0 = 128;

      function CHECK_FOR_FLAGS() {
        if (flags&FWIDTH) { $Kernel.$raise($$$('ArgumentError'), "flag after width"); }
        if (flags&FPREC0) { $Kernel.$raise($$$('ArgumentError'), "flag after precision"); }
      }

      function CHECK_FOR_WIDTH() {
        if (flags&FWIDTH) { $Kernel.$raise($$$('ArgumentError'), "width given twice"); }
        if (flags&FPREC0) { $Kernel.$raise($$$('ArgumentError'), "width after precision"); }
      }

      function GET_NTH_ARG(num) {
        if (num >= args.length) { $Kernel.$raise($$$('ArgumentError'), "too few arguments"); }
        return args[num];
      }

      function GET_NEXT_ARG() {
        switch (pos_arg_num) {
        case -1: $Kernel.$raise($$$('ArgumentError'), "unnumbered(" + (seq_arg_num) + ") mixed with numbered"); // raise
        case -2: $Kernel.$raise($$$('ArgumentError'), "unnumbered(" + (seq_arg_num) + ") mixed with named"); // raise
        }
        pos_arg_num = seq_arg_num++;
        return GET_NTH_ARG(pos_arg_num - 1);
      }

      function GET_POS_ARG(num) {
        if (pos_arg_num > 0) {
          $Kernel.$raise($$$('ArgumentError'), "numbered(" + (num) + ") after unnumbered(" + (pos_arg_num) + ")");
        }
        if (pos_arg_num === -2) {
          $Kernel.$raise($$$('ArgumentError'), "numbered(" + (num) + ") after named");
        }
        if (num < 1) {
          $Kernel.$raise($$$('ArgumentError'), "invalid index - " + (num) + "$");
        }
        pos_arg_num = -1;
        return GET_NTH_ARG(num - 1);
      }

      function GET_ARG() {
        return (next_arg === undefined ? GET_NEXT_ARG() : next_arg);
      }

      function READ_NUM(label) {
        var num, str = '';
        for (;; i++) {
          if (i === len) {
            $Kernel.$raise($$$('ArgumentError'), "malformed format string - %*[0-9]");
          }
          if (format_string.charCodeAt(i) < 48 || format_string.charCodeAt(i) > 57) {
            i--;
            num = parseInt(str, 10) || 0;
            if (num > 2147483647) {
              $Kernel.$raise($$$('ArgumentError'), "" + (label) + " too big");
            }
            return num;
          }
          str += format_string.charAt(i);
        }
      }

      function READ_NUM_AFTER_ASTER(label) {
        var arg, num = READ_NUM(label);
        if (format_string.charAt(i + 1) === '$') {
          i++;
          arg = GET_POS_ARG(num);
        } else {
          arg = GET_NEXT_ARG();
        }
        return (arg).$to_int();
      }

      for (i = format_string.indexOf('%'); i !== -1; i = format_string.indexOf('%', i)) {
        str = undefined;

        flags = FNONE;
        width = -1;
        precision = -1;
        next_arg = undefined;

        end_slice = i;

        i++;

        switch (format_string.charAt(i)) {
        case '%':
          begin_slice = i;
          // no-break
        case '':
        case '\n':
        case '\0':
          i++;
          continue;
        }

        format_sequence: for (; i < len; i++) {
          switch (format_string.charAt(i)) {

          case ' ':
            CHECK_FOR_FLAGS();
            flags |= FSPACE;
            continue format_sequence;

          case '#':
            CHECK_FOR_FLAGS();
            flags |= FSHARP;
            continue format_sequence;

          case '+':
            CHECK_FOR_FLAGS();
            flags |= FPLUS;
            continue format_sequence;

          case '-':
            CHECK_FOR_FLAGS();
            flags |= FMINUS;
            continue format_sequence;

          case '0':
            CHECK_FOR_FLAGS();
            flags |= FZERO;
            continue format_sequence;

          case '1':
          case '2':
          case '3':
          case '4':
          case '5':
          case '6':
          case '7':
          case '8':
          case '9':
            tmp_num = READ_NUM('width');
            if (format_string.charAt(i + 1) === '$') {
              if (i + 2 === len) {
                str = '%';
                i++;
                break format_sequence;
              }
              if (next_arg !== undefined) {
                $Kernel.$raise($$$('ArgumentError'), "value given twice - %" + (tmp_num) + "$");
              }
              next_arg = GET_POS_ARG(tmp_num);
              i++;
            } else {
              CHECK_FOR_WIDTH();
              flags |= FWIDTH;
              width = tmp_num;
            }
            continue format_sequence;

          case '<':
          case '\{':
            closing_brace_char = (format_string.charAt(i) === '<' ? '>' : '\}');
            hash_parameter_key = '';

            i++;

            for (;; i++) {
              if (i === len) {
                $Kernel.$raise($$$('ArgumentError'), "malformed name - unmatched parenthesis");
              }
              if (format_string.charAt(i) === closing_brace_char) {

                if (pos_arg_num > 0) {
                  $Kernel.$raise($$$('ArgumentError'), "named " + (hash_parameter_key) + " after unnumbered(" + (pos_arg_num) + ")");
                }
                if (pos_arg_num === -1) {
                  $Kernel.$raise($$$('ArgumentError'), "named " + (hash_parameter_key) + " after numbered");
                }
                pos_arg_num = -2;

                if (args[0] === undefined || !args[0].$$is_hash) {
                  $Kernel.$raise($$$('ArgumentError'), "one hash required");
                }

                next_arg = (args[0]).$fetch(hash_parameter_key);

                if (closing_brace_char === '>') {
                  continue format_sequence;
                } else {
                  str = next_arg.toString();
                  if (precision !== -1) { str = str.slice(0, precision); }
                  if (flags&FMINUS) {
                    while (str.length < width) { str = str + ' '; }
                  } else {
                    while (str.length < width) { str = ' ' + str; }
                  }
                  break format_sequence;
                }
              }
              hash_parameter_key += format_string.charAt(i);
            }
            // raise

          case '*':
            i++;
            CHECK_FOR_WIDTH();
            flags |= FWIDTH;
            width = READ_NUM_AFTER_ASTER('width');
            if (width < 0) {
              flags |= FMINUS;
              width = -width;
            }
            continue format_sequence;

          case '.':
            if (flags&FPREC0) {
              $Kernel.$raise($$$('ArgumentError'), "precision given twice");
            }
            flags |= FPREC|FPREC0;
            precision = 0;
            i++;
            if (format_string.charAt(i) === '*') {
              i++;
              precision = READ_NUM_AFTER_ASTER('precision');
              if (precision < 0) {
                flags &= ~FPREC;
              }
              continue format_sequence;
            }
            precision = READ_NUM('precision');
            continue format_sequence;

          case 'd':
          case 'i':
          case 'u':
            arg = $Kernel.$Integer(GET_ARG());
            if (arg >= 0) {
              str = arg.toString();
              while (str.length < precision) { str = '0' + str; }
              if (flags&FMINUS) {
                if (flags&FPLUS || flags&FSPACE) { str = (flags&FPLUS ? '+' : ' ') + str; }
                while (str.length < width) { str = str + ' '; }
              } else {
                if (flags&FZERO && precision === -1) {
                  while (str.length < width - ((flags&FPLUS || flags&FSPACE) ? 1 : 0)) { str = '0' + str; }
                  if (flags&FPLUS || flags&FSPACE) { str = (flags&FPLUS ? '+' : ' ') + str; }
                } else {
                  if (flags&FPLUS || flags&FSPACE) { str = (flags&FPLUS ? '+' : ' ') + str; }
                  while (str.length < width) { str = ' ' + str; }
                }
              }
            } else {
              str = (-arg).toString();
              while (str.length < precision) { str = '0' + str; }
              if (flags&FMINUS) {
                str = '-' + str;
                while (str.length < width) { str = str + ' '; }
              } else {
                if (flags&FZERO && precision === -1) {
                  while (str.length < width - 1) { str = '0' + str; }
                  str = '-' + str;
                } else {
                  str = '-' + str;
                  while (str.length < width) { str = ' ' + str; }
                }
              }
            }
            break format_sequence;

          case 'b':
          case 'B':
          case 'o':
          case 'x':
          case 'X':
            switch (format_string.charAt(i)) {
            case 'b':
            case 'B':
              base_number = 2;
              base_prefix = '0b';
              base_neg_zero_regex = /^1+/;
              base_neg_zero_digit = '1';
              break;
            case 'o':
              base_number = 8;
              base_prefix = '0';
              base_neg_zero_regex = /^3?7+/;
              base_neg_zero_digit = '7';
              break;
            case 'x':
            case 'X':
              base_number = 16;
              base_prefix = '0x';
              base_neg_zero_regex = /^f+/;
              base_neg_zero_digit = 'f';
              break;
            }
            arg = $Kernel.$Integer(GET_ARG());
            if (arg >= 0) {
              str = arg.toString(base_number);
              while (str.length < precision) { str = '0' + str; }
              if (flags&FMINUS) {
                if (flags&FPLUS || flags&FSPACE) { str = (flags&FPLUS ? '+' : ' ') + str; }
                if (flags&FSHARP && arg !== 0) { str = base_prefix + str; }
                while (str.length < width) { str = str + ' '; }
              } else {
                if (flags&FZERO && precision === -1) {
                  while (str.length < width - ((flags&FPLUS || flags&FSPACE) ? 1 : 0) - ((flags&FSHARP && arg !== 0) ? base_prefix.length : 0)) { str = '0' + str; }
                  if (flags&FSHARP && arg !== 0) { str = base_prefix + str; }
                  if (flags&FPLUS || flags&FSPACE) { str = (flags&FPLUS ? '+' : ' ') + str; }
                } else {
                  if (flags&FSHARP && arg !== 0) { str = base_prefix + str; }
                  if (flags&FPLUS || flags&FSPACE) { str = (flags&FPLUS ? '+' : ' ') + str; }
                  while (str.length < width) { str = ' ' + str; }
                }
              }
            } else {
              if (flags&FPLUS || flags&FSPACE) {
                str = (-arg).toString(base_number);
                while (str.length < precision) { str = '0' + str; }
                if (flags&FMINUS) {
                  if (flags&FSHARP) { str = base_prefix + str; }
                  str = '-' + str;
                  while (str.length < width) { str = str + ' '; }
                } else {
                  if (flags&FZERO && precision === -1) {
                    while (str.length < width - 1 - (flags&FSHARP ? 2 : 0)) { str = '0' + str; }
                    if (flags&FSHARP) { str = base_prefix + str; }
                    str = '-' + str;
                  } else {
                    if (flags&FSHARP) { str = base_prefix + str; }
                    str = '-' + str;
                    while (str.length < width) { str = ' ' + str; }
                  }
                }
              } else {
                str = (arg >>> 0).toString(base_number).replace(base_neg_zero_regex, base_neg_zero_digit);
                while (str.length < precision - 2) { str = base_neg_zero_digit + str; }
                if (flags&FMINUS) {
                  str = '..' + str;
                  if (flags&FSHARP) { str = base_prefix + str; }
                  while (str.length < width) { str = str + ' '; }
                } else {
                  if (flags&FZERO && precision === -1) {
                    while (str.length < width - 2 - (flags&FSHARP ? base_prefix.length : 0)) { str = base_neg_zero_digit + str; }
                    str = '..' + str;
                    if (flags&FSHARP) { str = base_prefix + str; }
                  } else {
                    str = '..' + str;
                    if (flags&FSHARP) { str = base_prefix + str; }
                    while (str.length < width) { str = ' ' + str; }
                  }
                }
              }
            }
            if (format_string.charAt(i) === format_string.charAt(i).toUpperCase()) {
              str = str.toUpperCase();
            }
            break format_sequence;

          case 'f':
          case 'e':
          case 'E':
          case 'g':
          case 'G':
            arg = $Kernel.$Float(GET_ARG());
            if (arg >= 0 || isNaN(arg)) {
              if (arg === Infinity) {
                str = 'Inf';
              } else {
                switch (format_string.charAt(i)) {
                case 'f':
                  str = arg.toFixed(precision === -1 ? 6 : precision);
                  break;
                case 'e':
                case 'E':
                  str = arg.toExponential(precision === -1 ? 6 : precision);
                  break;
                case 'g':
                case 'G':
                  str = arg.toExponential();
                  exponent = parseInt(str.split('e')[1], 10);
                  if (!(exponent < -4 || exponent >= (precision === -1 ? 6 : precision))) {
                    str = arg.toPrecision(precision === -1 ? (flags&FSHARP ? 6 : undefined) : precision);
                  }
                  break;
                }
              }
              if (flags&FMINUS) {
                if (flags&FPLUS || flags&FSPACE) { str = (flags&FPLUS ? '+' : ' ') + str; }
                while (str.length < width) { str = str + ' '; }
              } else {
                if (flags&FZERO && arg !== Infinity && !isNaN(arg)) {
                  while (str.length < width - ((flags&FPLUS || flags&FSPACE) ? 1 : 0)) { str = '0' + str; }
                  if (flags&FPLUS || flags&FSPACE) { str = (flags&FPLUS ? '+' : ' ') + str; }
                } else {
                  if (flags&FPLUS || flags&FSPACE) { str = (flags&FPLUS ? '+' : ' ') + str; }
                  while (str.length < width) { str = ' ' + str; }
                }
              }
            } else {
              if (arg === -Infinity) {
                str = 'Inf';
              } else {
                switch (format_string.charAt(i)) {
                case 'f':
                  str = (-arg).toFixed(precision === -1 ? 6 : precision);
                  break;
                case 'e':
                case 'E':
                  str = (-arg).toExponential(precision === -1 ? 6 : precision);
                  break;
                case 'g':
                case 'G':
                  str = (-arg).toExponential();
                  exponent = parseInt(str.split('e')[1], 10);
                  if (!(exponent < -4 || exponent >= (precision === -1 ? 6 : precision))) {
                    str = (-arg).toPrecision(precision === -1 ? (flags&FSHARP ? 6 : undefined) : precision);
                  }
                  break;
                }
              }
              if (flags&FMINUS) {
                str = '-' + str;
                while (str.length < width) { str = str + ' '; }
              } else {
                if (flags&FZERO && arg !== -Infinity) {
                  while (str.length < width - 1) { str = '0' + str; }
                  str = '-' + str;
                } else {
                  str = '-' + str;
                  while (str.length < width) { str = ' ' + str; }
                }
              }
            }
            if (format_string.charAt(i) === format_string.charAt(i).toUpperCase() && arg !== Infinity && arg !== -Infinity && !isNaN(arg)) {
              str = str.toUpperCase();
            }
            str = str.replace(/([eE][-+]?)([0-9])$/, '$10$2');
            break format_sequence;

          case 'a':
          case 'A':
            // Not implemented because there are no specs for this field type.
            $Kernel.$raise($$$('NotImplementedError'), "`A` and `a` format field types are not implemented in Opal yet");
            // raise

          case 'c':
            arg = GET_ARG();
            if ((arg)['$respond_to?']("to_ary")) { arg = (arg).$to_ary()[0]; }
            if ((arg)['$respond_to?']("to_str")) {
              str = (arg).$to_str();
            } else {
              str = String.fromCharCode($coerce_to(arg, $$$('Integer'), 'to_int'));
            }
            if (str.length !== 1) {
              $Kernel.$raise($$$('ArgumentError'), "%c requires a character");
            }
            if (flags&FMINUS) {
              while (str.length < width) { str = str + ' '; }
            } else {
              while (str.length < width) { str = ' ' + str; }
            }
            break format_sequence;

          case 'p':
            str = (GET_ARG()).$inspect();
            if (precision !== -1) { str = str.slice(0, precision); }
            if (flags&FMINUS) {
              while (str.length < width) { str = str + ' '; }
            } else {
              while (str.length < width) { str = ' ' + str; }
            }
            break format_sequence;

          case 's':
            str = (GET_ARG()).$to_s();
            if (precision !== -1) { str = str.slice(0, precision); }
            if (flags&FMINUS) {
              while (str.length < width) { str = str + ' '; }
            } else {
              while (str.length < width) { str = ' ' + str; }
            }
            break format_sequence;

          default:
            $Kernel.$raise($$$('ArgumentError'), "malformed format string - %" + (format_string.charAt(i)));
          }
        }

        if (str === undefined) {
          $Kernel.$raise($$$('ArgumentError'), "malformed format string - %");
        }

        result += format_string.slice(begin_slice, end_slice) + str;
        begin_slice = i + 1;
      }

      if ($gvars.DEBUG && pos_arg_num >= 0 && seq_arg_num < args.length) {
        $Kernel.$raise($$$('ArgumentError'), "too many arguments for format string");
      }

      return result + format_string.slice(begin_slice);
    }, -2);
    return $alias(self, "sprintf", "format");
  })('::')
};

Opal.modules["corelib/string/encoding"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $hash2 = Opal.hash2, $rb_plus = Opal.rb_plus, $truthy = Opal.truthy, $send = Opal.send, $defs = Opal.defs, $eqeq = Opal.eqeq, $def = Opal.def, $return_ivar = Opal.return_ivar, $return_val = Opal.return_val, $slice = Opal.slice, $Kernel = Opal.Kernel, $Opal = Opal.Opal, $rb_lt = Opal.rb_lt, $a, self = Opal.top, $nesting = [], $$ = Opal.$r($nesting), nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('require,+,[],clone,initialize,new,instance_eval,to_proc,each,const_set,tr,==,default_external,attr_accessor,singleton_class,attr_reader,raise,register,length,bytes,force_encoding,dup,bytesize,enum_for,each_byte,to_a,each_char,each_codepoint,coerce_to!,find,<,default_external=');
  
  self.$require("corelib/string");
  (function($base, $super) {
    var self = $klass($base, $super, 'Encoding');

    var $proto = self.$$prototype;

    $proto.name = $proto.dummy = nil;
    
    $defs(self, '$register', function $$register(name, options) {
      var block = $$register.$$p || nil, self = this, names = nil, $ret_or_1 = nil, ascii = nil, dummy = nil, encoding = nil, register = nil;

      $$register.$$p = null;
      if (options == null) options = $hash2([], {});
      names = $rb_plus([name], ($truthy(($ret_or_1 = options['$[]']("aliases"))) ? ($ret_or_1) : ([])));
      ascii = ($truthy(($ret_or_1 = options['$[]']("ascii"))) && ($ret_or_1));
      dummy = ($truthy(($ret_or_1 = options['$[]']("dummy"))) && ($ret_or_1));
      if ($truthy(options['$[]']("inherits"))) {
        
        encoding = options['$[]']("inherits").$clone();
        encoding.$initialize(name, names, ascii, dummy);
      } else {
        encoding = self.$new(name, names, ascii, dummy);
      }      if ((block !== nil)) {
        $send(encoding, 'instance_eval', [], block.$to_proc());
      }      register = Opal.encodings;
      return $send(names, 'each', [], function $$1(encoding_name){var self = $$1.$$s == null ? this : $$1.$$s;

        
        if (encoding_name == null) encoding_name = nil;
        self.$const_set(encoding_name.$tr("-", "_"), encoding);
        return register[encoding_name] = encoding;}, {$$s: self});
    }, -2);
    $defs(self, '$find', function $$find(name) {
      var self = this;

      
      if ($eqeq(name, "default_external")) {
        return self.$default_external()
      }      return Opal.find_encoding(name);    });
    self.$singleton_class().$attr_accessor("default_external");
    self.$attr_reader("name", "names");
    
    $def(self, '$initialize', function $$initialize(name, names, ascii, dummy) {
      var self = this;

      
      self.name = name;
      self.names = names;
      self.ascii = ascii;
      return (self.dummy = dummy);
    });
    
    $def(self, '$ascii_compatible?', $return_ivar("ascii"));
    
    $def(self, '$dummy?', $return_ivar("dummy"));
    
    $def(self, '$binary?', $return_val(false));
    
    $def(self, '$to_s', $return_ivar("name"));
    
    $def(self, '$inspect', function $$inspect() {
      var self = this;

      return "#<Encoding:" + (self.name) + (($truthy(self.dummy) ? (" (dummy)") : nil)) + ">"
    });
    
    $def(self, '$charsize', function $$charsize(string) {
      
      
      var len = 0;
      for (var i = 0, length = string.length; i < length; i++) {
        var charcode = string.charCodeAt(i);
        if (!(charcode >= 0xD800 && charcode <= 0xDBFF)) {
          len++;
        }
      }
      return len;
    
    });
    
    $def(self, '$each_char', function $$each_char(string) {
      var block = $$each_char.$$p || nil;

      $$each_char.$$p = null;
      
      var low_surrogate = "";
      for (var i = 0, length = string.length; i < length; i++) {
        var charcode = string.charCodeAt(i);
        var chr = string.charAt(i);
        if (charcode >= 0xDC00 && charcode <= 0xDFFF) {
          low_surrogate = chr;
          continue;
        }
        else if (charcode >= 0xD800 && charcode <= 0xDBFF) {
          chr = low_surrogate + chr;
        }
        if (string.encoding.name != "UTF-8") {
          chr = new String(chr);
          chr.encoding = string.encoding;
        }
        Opal.yield1(block, chr);
      }
    });
    
    $def(self, '$each_byte', function $$each_byte($a) {

      
      $slice(arguments);
      return $Kernel.$raise($$$('NotImplementedError'));
    }, -1);
    
    $def(self, '$bytesize', function $$bytesize($a) {

      
      $slice(arguments);
      return $Kernel.$raise($$$('NotImplementedError'));
    }, -1);
    $klass('::', $$$('StandardError'), 'EncodingError');
    return ($klass('::', $$$('EncodingError'), 'CompatibilityError'), nil);
  })('::', null);
  $send($$$('Encoding'), 'register', ["UTF-8", $hash2(["aliases", "ascii"], {"aliases": ["CP65001"], "ascii": true})], function $$2(){var self = $$2.$$s == null ? this : $$2.$$s;

    
    
    $def(self, '$each_byte', function $$each_byte(string) {
      var block = $$each_byte.$$p || nil;

      $$each_byte.$$p = null;
      
      // Taken from: https://github.com/feross/buffer/blob/f52dffd9df0445b93c0c9065c2f8f0f46b2c729a/index.js#L1954-L2032
      var units = Infinity;
      var codePoint;
      var length = string.length;
      var leadSurrogate = null;

      for (var i = 0; i < length; ++i) {
        codePoint = string.charCodeAt(i);

        // is surrogate component
        if (codePoint > 0xD7FF && codePoint < 0xE000) {
          // last char was a lead
          if (!leadSurrogate) {
            // no lead yet
            if (codePoint > 0xDBFF) {
              // unexpected trail
              if ((units -= 3) > -1) {
                Opal.yield1(block, 0xEF);
                Opal.yield1(block, 0xBF);
                Opal.yield1(block, 0xBD);
              }
              continue;
            } else if (i + 1 === length) {
              // unpaired lead
              if ((units -= 3) > -1) {
                Opal.yield1(block, 0xEF);
                Opal.yield1(block, 0xBF);
                Opal.yield1(block, 0xBD);
              }
              continue;
            }

            // valid lead
            leadSurrogate = codePoint;

            continue;
          }

          // 2 leads in a row
          if (codePoint < 0xDC00) {
            if ((units -= 3) > -1) {
              Opal.yield1(block, 0xEF);
              Opal.yield1(block, 0xBF);
              Opal.yield1(block, 0xBD);
            }
            leadSurrogate = codePoint;
            continue;
          }

          // valid surrogate pair
          codePoint = (leadSurrogate - 0xD800 << 10 | codePoint - 0xDC00) + 0x10000;
        } else if (leadSurrogate) {
          // valid bmp char, but last char was a lead
          if ((units -= 3) > -1) {
            Opal.yield1(block, 0xEF);
            Opal.yield1(block, 0xBF);
            Opal.yield1(block, 0xBD);
          }
        }

        leadSurrogate = null;

        // encode utf8
        if (codePoint < 0x80) {
          if ((units -= 1) < 0) break;
          Opal.yield1(block, codePoint);
        } else if (codePoint < 0x800) {
          if ((units -= 2) < 0) break;
          Opal.yield1(block, codePoint >> 0x6 | 0xC0);
          Opal.yield1(block, codePoint & 0x3F | 0x80);
        } else if (codePoint < 0x10000) {
          if ((units -= 3) < 0) break;
          Opal.yield1(block, codePoint >> 0xC | 0xE0);
          Opal.yield1(block, codePoint >> 0x6 & 0x3F | 0x80);
          Opal.yield1(block, codePoint & 0x3F | 0x80);
        } else if (codePoint < 0x110000) {
          if ((units -= 4) < 0) break;
          Opal.yield1(block, codePoint >> 0x12 | 0xF0);
          Opal.yield1(block, codePoint >> 0xC & 0x3F | 0x80);
          Opal.yield1(block, codePoint >> 0x6 & 0x3F | 0x80);
          Opal.yield1(block, codePoint & 0x3F | 0x80);
        } else ;
      }
    });
    return $def(self, '$bytesize', function $$bytesize(string) {
      
      return string.$bytes().$length()
    });}, {$$s: self});
  $send($$$('Encoding'), 'register', ["UTF-16LE"], function $$3(){var self = $$3.$$s == null ? this : $$3.$$s;

    
    
    $def(self, '$each_byte', function $$each_byte(string) {
      var block = $$each_byte.$$p || nil;

      $$each_byte.$$p = null;
      
      for (var i = 0, length = string.length; i < length; i++) {
        var code = string.charCodeAt(i);

        Opal.yield1(block, code & 0xff);
        Opal.yield1(block, code >> 8);
      }
    });
    return $def(self, '$bytesize', function $$bytesize(string) {
      
      return string.length * 2;
    });}, {$$s: self});
  $send($$$('Encoding'), 'register', ["UTF-16BE", $hash2(["inherits"], {"inherits": $$$($$$('Encoding'), 'UTF_16LE')})], function $$4(){var self = $$4.$$s == null ? this : $$4.$$s;

    return $def(self, '$each_byte', function $$each_byte(string) {
      var block = $$each_byte.$$p || nil;

      $$each_byte.$$p = null;
      
      for (var i = 0, length = string.length; i < length; i++) {
        var code = string.charCodeAt(i);

        Opal.yield1(block, code >> 8);
        Opal.yield1(block, code & 0xff);
      }
    })}, {$$s: self});
  $send($$$('Encoding'), 'register', ["UTF-32LE"], function $$5(){var self = $$5.$$s == null ? this : $$5.$$s;

    
    
    $def(self, '$each_byte', function $$each_byte(string) {
      var block = $$each_byte.$$p || nil;

      $$each_byte.$$p = null;
      
      for (var i = 0, length = string.length; i < length; i++) {
        var code = string.charCodeAt(i);

        Opal.yield1(block, code & 0xff);
        Opal.yield1(block, code >> 8);
        Opal.yield1(block, 0);
        Opal.yield1(block, 0);
      }
    });
    return $def(self, '$bytesize', function $$bytesize(string) {
      
      return string.length * 4;
    });}, {$$s: self});
  $send($$$('Encoding'), 'register', ["UTF-32BE", $hash2(["inherits"], {"inherits": $$$($$$('Encoding'), 'UTF_32LE')})], function $$6(){var self = $$6.$$s == null ? this : $$6.$$s;

    return $def(self, '$each_byte', function $$each_byte(string) {
      var block = $$each_byte.$$p || nil;

      $$each_byte.$$p = null;
      
      for (var i = 0, length = string.length; i < length; i++) {
        var code = string.charCodeAt(i);

        Opal.yield1(block, 0);
        Opal.yield1(block, 0);
        Opal.yield1(block, code >> 8);
        Opal.yield1(block, code & 0xff);
      }
    })}, {$$s: self});
  $send($$$('Encoding'), 'register', ["ASCII-8BIT", $hash2(["aliases", "ascii"], {"aliases": ["BINARY"], "ascii": true})], function $$7(){var self = $$7.$$s == null ? this : $$7.$$s;

    
    
    $def(self, '$each_char', function $$each_char(string) {
      var block = $$each_char.$$p || nil;

      $$each_char.$$p = null;
      
      for (var i = 0, length = string.length; i < length; i++) {
        var chr = new String(string.charAt(i));
        chr.encoding = string.encoding;
        Opal.yield1(block, chr);
      }
    });
    
    $def(self, '$charsize', function $$charsize(string) {
      
      return string.length;
    });
    
    $def(self, '$each_byte', function $$each_byte(string) {
      var block = $$each_byte.$$p || nil;

      $$each_byte.$$p = null;
      
      for (var i = 0, length = string.length; i < length; i++) {
        var code = string.charCodeAt(i);
        Opal.yield1(block, code & 0xff);
      }
    });
    
    $def(self, '$bytesize', function $$bytesize(string) {
      
      return string.length;
    });
    return $def(self, '$binary?', $return_val(true));}, {$$s: self});
  $$$('Encoding').$register("ISO-8859-1", $hash2(["aliases", "ascii", "inherits"], {"aliases": ["ISO8859-1"], "ascii": true, "inherits": $$$($$$('Encoding'), 'ASCII_8BIT')}));
  $$$('Encoding').$register("US-ASCII", $hash2(["aliases", "ascii", "inherits"], {"aliases": ["ASCII"], "ascii": true, "inherits": $$$($$$('Encoding'), 'ASCII_8BIT')}));
  (function($base, $super) {
    var self = $klass($base, $super, 'String');

    var $proto = self.$$prototype;

    $proto.internal_encoding = $proto.bytes = $proto.encoding = nil;
    
    self.$attr_reader("encoding");
    self.$attr_reader("internal_encoding");
    Opal.prop(String.prototype, 'bytes', nil);
    Opal.prop(String.prototype, 'encoding', $$$($$$('Encoding'), 'UTF_8'));
    Opal.prop(String.prototype, 'internal_encoding', $$$($$$('Encoding'), 'UTF_8'));
    
    $def(self, '$b', function $$b() {
      var self = this;

      return self.$dup().$force_encoding("binary")
    });
    
    $def(self, '$bytesize', function $$bytesize() {
      var self = this;

      return self.internal_encoding.$bytesize(self)
    });
    
    $def(self, '$each_byte', function $$each_byte() {
      var block = $$each_byte.$$p || nil, self = this;

      $$each_byte.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["each_byte"], function $$8(){var self = $$8.$$s == null ? this : $$8.$$s;

          return self.$bytesize()}, {$$s: self})
      }      $send(self.internal_encoding, 'each_byte', [self], block.$to_proc());
      return self;
    });
    
    $def(self, '$bytes', function $$bytes() {
      var self = this, $ret_or_1 = nil;

      
      
      if (typeof self === 'string') {
        return (new String(self)).$each_byte().$to_a();
      }
      self.bytes = ($truthy(($ret_or_1 = self.bytes)) ? ($ret_or_1) : (self.$each_byte().$to_a()));
      return self.bytes.$dup();
    });
    
    $def(self, '$each_char', function $$each_char() {
      var block = $$each_char.$$p || nil, self = this;

      $$each_char.$$p = null;
      if (!(block !== nil)) {
        return $send(self, 'enum_for', ["each_char"], function $$9(){var self = $$9.$$s == null ? this : $$9.$$s;

          return self.$length()}, {$$s: self})
      }      $send(self.encoding, 'each_char', [self], block.$to_proc());
      return self;
    });
    
    $def(self, '$chars', function $$chars() {
      var block = $$chars.$$p || nil, self = this;

      $$chars.$$p = null;
      if (!$truthy(block)) {
        return self.$each_char().$to_a()
      }      return $send(self, 'each_char', [], block.$to_proc());
    });
    
    $def(self, '$each_codepoint', function $$each_codepoint() {
      var block = $$each_codepoint.$$p || nil, self = this;

      $$each_codepoint.$$p = null;
      if (!(block !== nil)) {
        return self.$enum_for("each_codepoint")
      }      
      for (var i = 0, length = self.length; i < length; i++) {
        Opal.yield1(block, self.codePointAt(i));
      }
      return self;
    });
    
    $def(self, '$codepoints', function $$codepoints() {
      var block = $$codepoints.$$p || nil, self = this;

      $$codepoints.$$p = null;
      if ((block !== nil)) {
        return $send(self, 'each_codepoint', [], block.$to_proc())
      }      return self.$each_codepoint().$to_a();
    });
    
    $def(self, '$encode', function $$encode(encoding) {
      var self = this;

      return Opal.enc(self, encoding);
    });
    
    $def(self, '$force_encoding', function $$force_encoding(encoding) {
      var self = this;

      
      var str = self;

      if (encoding === str.encoding) { return str; }

      encoding = $Opal['$coerce_to!'](encoding, $$$('String'), "to_s");
      encoding = $$$('Encoding').$find(encoding);

      if (encoding === str.encoding) { return str; }

      str = Opal.set_encoding(str, encoding);

      return str;
    
    });
    
    $def(self, '$getbyte', function $$getbyte(idx) {
      var self = this, string_bytes = nil;

      
      string_bytes = self.$bytes();
      idx = $Opal['$coerce_to!'](idx, $$$('Integer'), "to_int");
      if ($truthy($rb_lt(string_bytes.$length(), idx))) {
        return nil
      }      return string_bytes['$[]'](idx);
    });
    
    $def(self, '$initialize_copy', function $$initialize_copy(other) {
      
      return "\n" + "      self.encoding = other.encoding;\n" + "      self.internal_encoding = other.internal_encoding;\n" + "    "
    });
    return $def(self, '$valid_encoding?', $return_val(true));
  })('::', null);
  return ($a = [$$$($$('Encoding'), 'UTF_8')], $send($$$('Encoding'), 'default_external=', $a), $a[$a.length - 1]);
};

Opal.modules["corelib/math"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $type_error = Opal.type_error, $module = Opal.module, $const_set = Opal.const_set, $Class = Opal.Class, $slice = Opal.slice, $Kernel = Opal.Kernel, $defs = Opal.defs, $truthy = Opal.truthy, $send = Opal.send, $def = Opal.def, $rb_minus = Opal.rb_minus, $eqeqeq = Opal.eqeqeq, $rb_divide = Opal.rb_divide, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('new,raise,Float,Integer,module_function,each,define_method,checked,float!,===,gamma,-,integer!,/,infinite?');
  return (function($base, $parent_nesting) {
    var self = $module($base, 'Math');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting);

    
    $const_set(self, 'E', Math.E);
    $const_set(self, 'PI', Math.PI);
    $const_set(self, 'DomainError', $Class.$new($$$('StandardError')));
    $defs(self, '$checked', function $$checked(method, $a) {
      var $post_args, args;

      
      $post_args = $slice(arguments, 1);
      args = $post_args;
      
      if (isNaN(args[0]) || (args.length == 2 && isNaN(args[1]))) {
        return NaN;
      }

      var result = Math[method].apply(null, args);

      if (isNaN(result)) {
        $Kernel.$raise($$('DomainError'), "Numerical argument is out of domain - \"" + (method) + "\"");
      }

      return result;
    }, -2);
    $defs(self, '$float!', function $Math_float$excl$1(value) {
      
      try {
        return $Kernel.$Float(value)
      } catch ($err) {
        if (Opal.rescue($err, [$$$('ArgumentError')])) {
          try {
            return $Kernel.$raise($type_error(value, $$$('Float')))
          } finally { Opal.pop_exception(); }
        } else { throw $err; }
      }
    });
    $defs(self, '$integer!', function $Math_integer$excl$2(value) {
      
      try {
        return $Kernel.$Integer(value)
      } catch ($err) {
        if (Opal.rescue($err, [$$$('ArgumentError')])) {
          try {
            return $Kernel.$raise($type_error(value, $$$('Integer')))
          } finally { Opal.pop_exception(); }
        } else { throw $err; }
      }
    });
    self.$module_function();
    if (!$truthy((typeof(Math.erf) !== "undefined"))) {
      
      Opal.prop(Math, 'erf', function(x) {
        var A1 =  0.254829592,
            A2 = -0.284496736,
            A3 =  1.421413741,
            A4 = -1.453152027,
            A5 =  1.061405429,
            P  =  0.3275911;

        var sign = 1;

        if (x < 0) {
            sign = -1;
        }

        x = Math.abs(x);

        var t = 1.0 / (1.0 + P * x);
        var y = 1.0 - (((((A5 * t + A4) * t) + A3) * t + A2) * t + A1) * t * Math.exp(-x * x);

        return sign * y;
      });
    
    }    if (!$truthy((typeof(Math.erfc) !== "undefined"))) {
      
      Opal.prop(Math, 'erfc', function(x) {
        var z = Math.abs(x),
            t = 1.0 / (0.5 * z + 1.0);

        var A1 = t * 0.17087277 + -0.82215223,
            A2 = t * A1 + 1.48851587,
            A3 = t * A2 + -1.13520398,
            A4 = t * A3 + 0.27886807,
            A5 = t * A4 + -0.18628806,
            A6 = t * A5 + 0.09678418,
            A7 = t * A6 + 0.37409196,
            A8 = t * A7 + 1.00002368,
            A9 = t * A8,
            A10 = -z * z - 1.26551223 + A9;

        var a = t * Math.exp(A10);

        if (x < 0.0) {
          return 2.0 - a;
        }
        else {
          return a;
        }
      });
    
    }    $send(["acos", "acosh", "asin", "asinh", "atan", "atanh", "cbrt", "cos", "cosh", "erf", "erfc", "exp", "sin", "sinh", "sqrt", "tanh"], 'each', [], function $Math$3(method){var self = $Math$3.$$s == null ? this : $Math$3.$$s;

      
      if (method == null) method = nil;
      return $send(self, 'define_method', [method], function $$4(x){
        
        if (x == null) x = nil;
        return $$$('Math').$checked(method, $$$('Math')['$float!'](x));});}, {$$s: self});
    
    $def(self, '$atan2', function $$atan2(y, x) {
      
      return $$$('Math').$checked("atan2", $$$('Math')['$float!'](y), $$$('Math')['$float!'](x))
    });
    
    $def(self, '$hypot', function $$hypot(x, y) {
      
      return $$$('Math').$checked("hypot", $$$('Math')['$float!'](x), $$$('Math')['$float!'](y))
    });
    
    $def(self, '$frexp', function $$frexp(x) {
      
      
      x = $$('Math')['$float!'](x);
      
      if (isNaN(x)) {
        return [NaN, 0];
      }

      var ex   = Math.floor(Math.log(Math.abs(x)) / Math.log(2)) + 1,
          frac = x / Math.pow(2, ex);

      return [frac, ex];
    });
    
    $def(self, '$gamma', function $$gamma(n) {
      
      
      n = $$('Math')['$float!'](n);
      
      var i, t, x, value, result, twoN, threeN, fourN, fiveN;

      var G = 4.7421875;

      var P = [
         0.99999999999999709182,
         57.156235665862923517,
        -59.597960355475491248,
         14.136097974741747174,
        -0.49191381609762019978,
         0.33994649984811888699e-4,
         0.46523628927048575665e-4,
        -0.98374475304879564677e-4,
         0.15808870322491248884e-3,
        -0.21026444172410488319e-3,
         0.21743961811521264320e-3,
        -0.16431810653676389022e-3,
         0.84418223983852743293e-4,
        -0.26190838401581408670e-4,
         0.36899182659531622704e-5
      ];


      if (isNaN(n)) {
        return NaN;
      }

      if (n === 0 && 1 / n < 0) {
        return -Infinity;
      }

      if (n === -1 || n === -Infinity) {
        $Kernel.$raise($$('DomainError'), "Numerical argument is out of domain - \"gamma\"");
      }

      if ($$('Integer')['$==='](n)) {
        if (n <= 0) {
          return isFinite(n) ? Infinity : NaN;
        }

        if (n > 171) {
          return Infinity;
        }

        value  = n - 2;
        result = n - 1;

        while (value > 1) {
          result *= value;
          value--;
        }

        if (result == 0) {
          result = 1;
        }

        return result;
      }

      if (n < 0.5) {
        return Math.PI / (Math.sin(Math.PI * n) * $$$('Math').$gamma($rb_minus(1, n)));
      }

      if (n >= 171.35) {
        return Infinity;
      }

      if (n > 85.0) {
        twoN   = n * n;
        threeN = twoN * n;
        fourN  = threeN * n;
        fiveN  = fourN * n;

        return Math.sqrt(2 * Math.PI / n) * Math.pow((n / Math.E), n) *
          (1 + 1 / (12 * n) + 1 / (288 * twoN) - 139 / (51840 * threeN) -
          571 / (2488320 * fourN) + 163879 / (209018880 * fiveN) +
          5246819 / (75246796800 * fiveN * n));
      }

      n -= 1;
      x  = P[0];

      for (i = 1; i < P.length; ++i) {
        x += P[i] / (n + i);
      }

      t = n + G + 0.5;

      return Math.sqrt(2 * Math.PI) * Math.pow(t, n + 0.5) * Math.exp(-t) * x;
    });
    
    $def(self, '$ldexp', function $$ldexp(mantissa, exponent) {
      
      
      mantissa = $$('Math')['$float!'](mantissa);
      exponent = $$('Math')['$integer!'](exponent);
      
      if (isNaN(exponent)) {
        $Kernel.$raise($$$('RangeError'), "float NaN out of range of integer");
      }

      return mantissa * Math.pow(2, exponent);
    });
    
    $def(self, '$lgamma', function $$lgamma(n) {
      
      
      if (n == -1) {
        return [Infinity, 1];
      }
      else {
        return [Math.log(Math.abs($$$('Math').$gamma(n))), $$$('Math').$gamma(n) < 0 ? -1 : 1];
      }
    
    });
    
    $def(self, '$log', function $$log(x, base) {
      if ($eqeqeq($$$('String'), x)) {
        $Kernel.$raise($type_error(x, $$$('Float')));
      }      if ($truthy(base == null)) {
        return $$$('Math').$checked("log", $$$('Math')['$float!'](x))
      } else {
        
        if ($eqeqeq($$$('String'), base)) {
          $Kernel.$raise($type_error(base, $$$('Float')));
        }        return $rb_divide($$$('Math').$checked("log", $$$('Math')['$float!'](x)), $$$('Math').$checked("log", $$$('Math')['$float!'](base)));
      }    }, -2);
    
    $def(self, '$log10', function $$log10(x) {
      
      
      if ($eqeqeq($$$('String'), x)) {
        $Kernel.$raise($type_error(x, $$$('Float')));
      }      return $$$('Math').$checked("log10", $$$('Math')['$float!'](x));
    });
    
    $def(self, '$log2', function $$log2(x) {
      
      
      if ($eqeqeq($$$('String'), x)) {
        $Kernel.$raise($type_error(x, $$$('Float')));
      }      return $$$('Math').$checked("log2", $$$('Math')['$float!'](x));
    });
    return $def(self, '$tan', function $$tan(x) {
      
      
      x = $$$('Math')['$float!'](x);
      if ($truthy(x['$infinite?']())) {
        return $$$($$$('Float'), 'NAN')
      }      return $$$('Math').$checked("tan", $$$('Math')['$float!'](x));
    });
  })('::', $nesting)
};

Opal.modules["corelib/complex/base"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $module = Opal.module, $truthy = Opal.truthy, $def = Opal.def, $klass = Opal.klass, $nesting = [], nil = Opal.nil;

  Opal.add_stubs('new,from_string');
  
  (function($base, $parent_nesting) {
    var self = $module($base, 'Kernel');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting);

    return $def(self, '$Complex', function $$Complex(real, imag) {
      
      
      if (imag == null) imag = nil;
      if ($truthy(imag)) {
        return $$('Complex').$new(real, imag)
      } else {
        return $$('Complex').$new(real, 0)
      }    }, -2)
  })('::', $nesting);
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'String');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting);

    return $def(self, '$to_c', function $$to_c() {
      var self = this;

      return $$('Complex').$from_string(self)
    })
  })('::', null, $nesting);
};

Opal.modules["corelib/complex"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $truthy = Opal.truthy, $eqeqeq = Opal.eqeqeq, $Kernel = Opal.Kernel, $defs = Opal.defs, $rb_times = Opal.rb_times, $def = Opal.def, $rb_plus = Opal.rb_plus, $rb_minus = Opal.rb_minus, $rb_divide = Opal.rb_divide, $eqeq = Opal.eqeq, $to_ary = Opal.to_ary, $rb_gt = Opal.rb_gt, $neqeq = Opal.neqeq, $return_val = Opal.return_val, $const_set = Opal.const_set, $alias = Opal.alias, self = Opal.top, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('require,real?,===,raise,new,*,cos,sin,attr_reader,freeze,class,==,real,imag,Complex,-@,+,__coerced__,-,nan?,/,conj,abs2,quo,polar,exp,log,>,!=,divmod,**,hypot,atan2,lcm,denominator,finite?,infinite?,numerator,abs,arg,rationalize,to_f,to_i,to_r,inspect,zero?,positive?,Rational,rect,angle');
  
  self.$require("corelib/numeric");
  self.$require("corelib/complex/base");
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Complex');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting), $proto = self.$$prototype;

    $proto.real = $proto.imag = nil;
    
    $defs(self, '$rect', function $$rect(real, imag) {
      var self = this;

      
      if (imag == null) imag = 0;
      if (!((($eqeqeq($$$('Numeric'), real) && ($truthy(real['$real?']()))) && ($eqeqeq($$$('Numeric'), imag))) && ($truthy(imag['$real?']())))) {
        $Kernel.$raise($$$('TypeError'), "not a real");
      }      return self.$new(real, imag);
    }, -2);
    $defs(self, '$polar', function $$polar(r, theta) {
      var self = this;

      
      if (theta == null) theta = 0;
      if (!((($eqeqeq($$$('Numeric'), r) && ($truthy(r['$real?']()))) && ($eqeqeq($$$('Numeric'), theta))) && ($truthy(theta['$real?']())))) {
        $Kernel.$raise($$$('TypeError'), "not a real");
      }      return self.$new($rb_times(r, $$$('Math').$cos(theta)), $rb_times(r, $$$('Math').$sin(theta)));
    }, -2);
    self.$attr_reader("real", "imag");
    
    $def(self, '$initialize', function $$initialize(real, imag) {
      var self = this;

      
      if (imag == null) imag = 0;
      self.real = real;
      self.imag = imag;
      return self.$freeze();
    }, -2);
    
    $def(self, '$coerce', function $$coerce(other) {
      var self = this;

      if ($eqeqeq($$$('Complex'), other)) {
        return [other, self]
      } else if (($eqeqeq($$$('Numeric'), other) && ($truthy(other['$real?']())))) {
        return [$$$('Complex').$new(other, 0), self]
      } else {
        return $Kernel.$raise($$$('TypeError'), "" + (other.$class()) + " can't be coerced into Complex")
      }
    });
    
    $def(self, '$==', function $Complex_$eq_eq$1(other) {
      var self = this, $ret_or_1 = nil;

      if ($eqeqeq($$$('Complex'), other)) {
        if ($truthy(($ret_or_1 = self.real['$=='](other.$real())))) {
          return self.imag['$=='](other.$imag())
        } else {
          return $ret_or_1
        }
      } else if (($eqeqeq($$$('Numeric'), other) && ($truthy(other['$real?']())))) {
        if ($truthy(($ret_or_1 = self.real['$=='](other)))) {
          return self.imag['$=='](0)
        } else {
          return $ret_or_1
        }
      } else {
        return other['$=='](self)
      }
    });
    
    $def(self, '$-@', function $Complex_$minus$$2() {
      var self = this;

      return $Kernel.$Complex(self.real['$-@'](), self.imag['$-@']())
    });
    
    $def(self, '$+', function $Complex_$plus$3(other) {
      var self = this;

      if ($eqeqeq($$$('Complex'), other)) {
        return $Kernel.$Complex($rb_plus(self.real, other.$real()), $rb_plus(self.imag, other.$imag()))
      } else if (($eqeqeq($$$('Numeric'), other) && ($truthy(other['$real?']())))) {
        return $Kernel.$Complex($rb_plus(self.real, other), self.imag)
      } else {
        return self.$__coerced__("+", other)
      }
    });
    
    $def(self, '$-', function $Complex_$minus$4(other) {
      var self = this;

      if ($eqeqeq($$$('Complex'), other)) {
        return $Kernel.$Complex($rb_minus(self.real, other.$real()), $rb_minus(self.imag, other.$imag()))
      } else if (($eqeqeq($$$('Numeric'), other) && ($truthy(other['$real?']())))) {
        return $Kernel.$Complex($rb_minus(self.real, other), self.imag)
      } else {
        return self.$__coerced__("-", other)
      }
    });
    
    $def(self, '$*', function $Complex_$$5(other) {
      var self = this;

      if ($eqeqeq($$$('Complex'), other)) {
        return $Kernel.$Complex($rb_minus($rb_times(self.real, other.$real()), $rb_times(self.imag, other.$imag())), $rb_plus($rb_times(self.real, other.$imag()), $rb_times(self.imag, other.$real())))
      } else if (($eqeqeq($$$('Numeric'), other) && ($truthy(other['$real?']())))) {
        return $Kernel.$Complex($rb_times(self.real, other), $rb_times(self.imag, other))
      } else {
        return self.$__coerced__("*", other)
      }
    });
    
    $def(self, '$/', function $Complex_$slash$6(other) {
      var self = this;

      if ($eqeqeq($$$('Complex'), other)) {
        if ((((($eqeqeq($$$('Number'), self.real) && ($truthy(self.real['$nan?']()))) || (($eqeqeq($$$('Number'), self.imag) && ($truthy(self.imag['$nan?']()))))) || (($eqeqeq($$$('Number'), other.$real()) && ($truthy(other.$real()['$nan?']()))))) || (($eqeqeq($$$('Number'), other.$imag()) && ($truthy(other.$imag()['$nan?']())))))) {
          return $$$('Complex').$new($$$($$$('Float'), 'NAN'), $$$($$$('Float'), 'NAN'))
        } else {
          return $rb_divide($rb_times(self, other.$conj()), other.$abs2())
        }
      } else if (($eqeqeq($$$('Numeric'), other) && ($truthy(other['$real?']())))) {
        return $Kernel.$Complex(self.real.$quo(other), self.imag.$quo(other))
      } else {
        return self.$__coerced__("/", other)
      }
    });
    
    $def(self, '$**', function $Complex_$$$7(other) {
      var $a, $b, self = this, r = nil, theta = nil, ore = nil, oim = nil, nr = nil, ntheta = nil, x = nil, z = nil, n = nil, div = nil, mod = nil;

      
      if ($eqeq(other, 0)) {
        return $$$('Complex').$new(1, 0)
      }      if ($eqeqeq($$$('Complex'), other)) {
        
        $b = self.$polar(), $a = $to_ary($b), (r = ($a[0] == null ? nil : $a[0])), (theta = ($a[1] == null ? nil : $a[1]));
        ore = other.$real();
        oim = other.$imag();
        nr = $$$('Math').$exp($rb_minus($rb_times(ore, $$$('Math').$log(r)), $rb_times(oim, theta)));
        ntheta = $rb_plus($rb_times(theta, ore), $rb_times(oim, $$$('Math').$log(r)));
        return $$$('Complex').$polar(nr, ntheta);
      } else if ($eqeqeq($$$('Integer'), other)) {
        if ($truthy($rb_gt(other, 0))) {
          
          x = self;
          z = x;
          n = $rb_minus(other, 1);
          while ($neqeq(n, 0)) {
          
            $b = n.$divmod(2), $a = $to_ary($b), (div = ($a[0] == null ? nil : $a[0])), (mod = ($a[1] == null ? nil : $a[1]));
            while ($eqeq(mod, 0)) {
            
              x = $Kernel.$Complex($rb_minus($rb_times(x.$real(), x.$real()), $rb_times(x.$imag(), x.$imag())), $rb_times($rb_times(2, x.$real()), x.$imag()));
              n = div;
              $b = n.$divmod(2), $a = $to_ary($b), (div = ($a[0] == null ? nil : $a[0])), (mod = ($a[1] == null ? nil : $a[1]));
            }            z = $rb_times(z, x);
            n = $rb_minus(n, 1);
          }          return z;
        } else {
          return $rb_divide($$$('Rational').$new(1, 1), self)['$**'](other['$-@']())
        }
      } else if (($eqeqeq($$$('Float'), other) || ($eqeqeq($$$('Rational'), other)))) {
        
        $b = self.$polar(), $a = $to_ary($b), (r = ($a[0] == null ? nil : $a[0])), (theta = ($a[1] == null ? nil : $a[1]));
        return $$$('Complex').$polar(r['$**'](other), $rb_times(theta, other));
      } else {
        return self.$__coerced__("**", other)
      }    });
    
    $def(self, '$abs', function $$abs() {
      var self = this;

      return $$$('Math').$hypot(self.real, self.imag)
    });
    
    $def(self, '$abs2', function $$abs2() {
      var self = this;

      return $rb_plus($rb_times(self.real, self.real), $rb_times(self.imag, self.imag))
    });
    
    $def(self, '$angle', function $$angle() {
      var self = this;

      return $$$('Math').$atan2(self.imag, self.real)
    });
    
    $def(self, '$conj', function $$conj() {
      var self = this;

      return $Kernel.$Complex(self.real, self.imag['$-@']())
    });
    
    $def(self, '$denominator', function $$denominator() {
      var self = this;

      return self.real.$denominator().$lcm(self.imag.$denominator())
    });
    
    $def(self, '$eql?', function $Complex_eql$ques$8(other) {
      var self = this, $ret_or_1 = nil, $ret_or_2 = nil;

      if ($truthy(($ret_or_1 = ($truthy(($ret_or_2 = $$('Complex')['$==='](other))) ? (self.real.$class()['$=='](self.imag.$class())) : ($ret_or_2))))) {
        return self['$=='](other)
      } else {
        return $ret_or_1
      }
    });
    
    $def(self, '$fdiv', function $$fdiv(other) {
      var self = this;

      
      if (!$eqeqeq($$$('Numeric'), other)) {
        $Kernel.$raise($$$('TypeError'), "" + (other.$class()) + " can't be coerced into Complex");
      }      return $rb_divide(self, other);
    });
    
    $def(self, '$finite?', function $Complex_finite$ques$9() {
      var self = this, $ret_or_1 = nil;

      if ($truthy(($ret_or_1 = self.real['$finite?']()))) {
        return self.imag['$finite?']()
      } else {
        return $ret_or_1
      }
    });
    
    $def(self, '$hash', function $$hash() {
      var self = this;

      return "Complex:" + (self.real) + ":" + (self.imag)
    });
    
    $def(self, '$infinite?', function $Complex_infinite$ques$10() {
      var self = this, $ret_or_1 = nil;

      if ($truthy(($ret_or_1 = self.real['$infinite?']()))) {
        return $ret_or_1
      } else {
        return self.imag['$infinite?']()
      }
    });
    
    $def(self, '$inspect', function $$inspect() {
      var self = this;

      return "(" + (self) + ")"
    });
    
    $def(self, '$numerator', function $$numerator() {
      var self = this, d = nil;

      
      d = self.$denominator();
      return $Kernel.$Complex($rb_times(self.real.$numerator(), $rb_divide(d, self.real.$denominator())), $rb_times(self.imag.$numerator(), $rb_divide(d, self.imag.$denominator())));
    });
    
    $def(self, '$polar', function $$polar() {
      var self = this;

      return [self.$abs(), self.$arg()]
    });
    
    $def(self, '$rationalize', function $$rationalize(eps) {
      var self = this;
      
      if (arguments.length > 1) {
        $Kernel.$raise($$$('ArgumentError'), "wrong number of arguments (" + (arguments.length) + " for 0..1)");
      }
      if ($neqeq(self.imag, 0)) {
        $Kernel.$raise($$$('RangeError'), "can't convert " + (self) + " into Rational");
      }      return self.$real().$rationalize(eps);
    }, -1);
    
    $def(self, '$real?', $return_val(false));
    
    $def(self, '$rect', function $$rect() {
      var self = this;

      return [self.real, self.imag]
    });
    
    $def(self, '$to_f', function $$to_f() {
      var self = this;

      
      if (!$eqeq(self.imag, 0)) {
        $Kernel.$raise($$$('RangeError'), "can't convert " + (self) + " into Float");
      }      return self.real.$to_f();
    });
    
    $def(self, '$to_i', function $$to_i() {
      var self = this;

      
      if (!$eqeq(self.imag, 0)) {
        $Kernel.$raise($$$('RangeError'), "can't convert " + (self) + " into Integer");
      }      return self.real.$to_i();
    });
    
    $def(self, '$to_r', function $$to_r() {
      var self = this;

      
      if (!$eqeq(self.imag, 0)) {
        $Kernel.$raise($$$('RangeError'), "can't convert " + (self) + " into Rational");
      }      return self.real.$to_r();
    });
    
    $def(self, '$to_s', function $$to_s() {
      var self = this, result = nil;

      
      result = self.real.$inspect();
      result = $rb_plus(result, (((($eqeqeq($$$('Number'), self.imag) && ($truthy(self.imag['$nan?']()))) || ($truthy(self.imag['$positive?']()))) || ($truthy(self.imag['$zero?']()))) ? ("+") : ("-")));
      result = $rb_plus(result, self.imag.$abs().$inspect());
      if (($eqeqeq($$$('Number'), self.imag) && (($truthy(self.imag['$nan?']()) || ($truthy(self.imag['$infinite?']())))))) {
        result = $rb_plus(result, "*");
      }      return $rb_plus(result, "i");
    });
    $const_set($nesting[0], 'I', self.$new(0, 1));
    $defs(self, '$from_string', function $$from_string(str) {
      
      
      var re = /[+-]?[\d_]+(\.[\d_]+)?(e\d+)?/;
          str.match(re);
          var real, imag;

      function isFloat() {
        return re.test(str);
      }

      function cutFloat() {
        var match = str.match(re);
        var number = match[0];
        str = str.slice(number.length);
        return number.replace(/_/g, '');
      }

      // handles both floats and rationals
      function cutNumber() {
        if (isFloat()) {
          var numerator = parseFloat(cutFloat());

          if (str[0] === '/') {
            // rational real part
            str = str.slice(1);

            if (isFloat()) {
              var denominator = parseFloat(cutFloat());
              return $Kernel.$Rational(numerator, denominator);
            } else {
              // reverting '/'
              str = '/' + str;
              return numerator;
            }
          } else {
            // float real part, no denominator
            return numerator;
          }
        } else {
          return null;
        }
      }

      real = cutNumber();

      if (!real) {
        if (str[0] === 'i') {
          // i => Complex(0, 1)
          return $Kernel.$Complex(0, 1);
        }
        if (str[0] === '-' && str[1] === 'i') {
          // -i => Complex(0, -1)
          return $Kernel.$Complex(0, -1);
        }
        if (str[0] === '+' && str[1] === 'i') {
          // +i => Complex(0, 1)
          return $Kernel.$Complex(0, 1);
        }
        // anything => Complex(0, 0)
        return $Kernel.$Complex(0, 0);
      }

      imag = cutNumber();
      if (!imag) {
        if (str[0] === 'i') {
          // 3i => Complex(0, 3)
          return $Kernel.$Complex(0, real);
        } else {
          // 3 => Complex(3, 0)
          return $Kernel.$Complex(real, 0);
        }
      } else {
        // 3+2i => Complex(3, 2)
        return $Kernel.$Complex(real, imag);
      }
    
    });
    (function(self, $parent_nesting) {
      
      return $alias(self, "rectangular", "rect")
    })(Opal.get_singleton_class(self));
    $alias(self, "arg", "angle");
    $alias(self, "conjugate", "conj");
    $alias(self, "divide", "/");
    $alias(self, "imaginary", "imag");
    $alias(self, "magnitude", "abs");
    $alias(self, "phase", "arg");
    $alias(self, "quo", "/");
    $alias(self, "rectangular", "rect");
    
    Opal.udef(self, '$' + "negative?");    
    Opal.udef(self, '$' + "positive?");    
    
    Opal.udef(self, '$' + "step");    return nil;  })('::', $$$('Numeric'), $nesting);
};

Opal.modules["corelib/rational/base"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $module = Opal.module, $def = Opal.def, $klass = Opal.klass; Opal.nil; var $$$ = Opal.$$$;

  Opal.add_stubs('convert,from_string');
  
  (function($base) {
    var self = $module($base, 'Kernel');

    
    return $def(self, '$Rational', function $$Rational(numerator, denominator) {
      
      
      if (denominator == null) denominator = 1;
      return $$$('Rational').$convert(numerator, denominator);
    }, -2)
  })('::');
  return (function($base, $super) {
    var self = $klass($base, $super, 'String');

    
    return $def(self, '$to_r', function $$to_r() {
      var self = this;

      return $$$('Rational').$from_string(self)
    })
  })('::', null);
};

Opal.modules["corelib/rational"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $eqeq = Opal.eqeq, $Kernel = Opal.Kernel, $truthy = Opal.truthy, $rb_lt = Opal.rb_lt, $rb_divide = Opal.rb_divide, $defs = Opal.defs, $eqeqeq = Opal.eqeqeq, $not = Opal.not, $Opal = Opal.Opal, $def = Opal.def, $return_ivar = Opal.return_ivar, $rb_minus = Opal.rb_minus, $rb_times = Opal.rb_times, $rb_plus = Opal.rb_plus, $rb_gt = Opal.rb_gt, $rb_le = Opal.rb_le, $return_self = Opal.return_self, $alias = Opal.alias, self = Opal.top, nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('require,to_i,==,raise,<,-@,new,gcd,/,nil?,===,reduce,to_r,!,equal?,coerce_to!,freeze,to_f,numerator,denominator,<=>,-,*,__coerced__,+,Rational,>,**,abs,ceil,with_precision,floor,<=,truncate,send');
  
  self.$require("corelib/numeric");
  self.$require("corelib/rational/base");
  return (function($base, $super) {
    var self = $klass($base, $super, 'Rational');

    var $proto = self.$$prototype;

    $proto.num = $proto.den = nil;
    
    $defs(self, '$reduce', function $$reduce(num, den) {
      var self = this, gcd = nil;

      
      num = num.$to_i();
      den = den.$to_i();
      if ($eqeq(den, 0)) {
        $Kernel.$raise($$$('ZeroDivisionError'), "divided by 0");
      } else if ($truthy($rb_lt(den, 0))) {
        
        num = num['$-@']();
        den = den['$-@']();
      } else if ($eqeq(den, 1)) {
        return self.$new(num, den)
      }      gcd = num.$gcd(den);
      return self.$new($rb_divide(num, gcd), $rb_divide(den, gcd));
    });
    $defs(self, '$convert', function $$convert(num, den) {
      var self = this;

      
      if (($truthy(num['$nil?']()) || ($truthy(den['$nil?']())))) {
        $Kernel.$raise($$$('TypeError'), "cannot convert nil into Rational");
      }      if (($eqeqeq($$$('Integer'), num) && ($eqeqeq($$$('Integer'), den)))) {
        return self.$reduce(num, den)
      }      if ((($eqeqeq($$$('Float'), num) || ($eqeqeq($$$('String'), num))) || ($eqeqeq($$$('Complex'), num)))) {
        num = num.$to_r();
      }      if ((($eqeqeq($$$('Float'), den) || ($eqeqeq($$$('String'), den))) || ($eqeqeq($$$('Complex'), den)))) {
        den = den.$to_r();
      }      if (($truthy(den['$equal?'](1)) && ($not($$$('Integer')['$==='](num))))) {
        return $Opal['$coerce_to!'](num, $$$('Rational'), "to_r")
      } else if (($eqeqeq($$$('Numeric'), num) && ($eqeqeq($$$('Numeric'), den)))) {
        return $rb_divide(num, den)
      } else {
        return self.$reduce(num, den)
      }    });
    
    $def(self, '$initialize', function $$initialize(num, den) {
      var self = this;

      
      self.num = num;
      self.den = den;
      return self.$freeze();
    });
    
    $def(self, '$numerator', $return_ivar("num"));
    
    $def(self, '$denominator', $return_ivar("den"));
    
    $def(self, '$coerce', function $$coerce(other) {
      var self = this, $ret_or_1 = nil;

      if ($eqeqeq($$$('Rational'), ($ret_or_1 = other))) {
        return [other, self]
      } else if ($eqeqeq($$$('Integer'), $ret_or_1)) {
        return [other.$to_r(), self]
      } else if ($eqeqeq($$$('Float'), $ret_or_1)) {
        return [other, self.$to_f()]
      } else {
        return nil
      }
    });
    
    $def(self, '$==', function $Rational_$eq_eq$1(other) {
      var self = this, $ret_or_1 = nil, $ret_or_2 = nil;

      if ($eqeqeq($$$('Rational'), ($ret_or_1 = other))) {
        if ($truthy(($ret_or_2 = self.num['$=='](other.$numerator())))) {
          return self.den['$=='](other.$denominator())
        } else {
          return $ret_or_2
        }
      } else if ($eqeqeq($$$('Integer'), $ret_or_1)) {
        if ($truthy(($ret_or_2 = self.num['$=='](other)))) {
          return self.den['$=='](1)
        } else {
          return $ret_or_2
        }
      } else if ($eqeqeq($$$('Float'), $ret_or_1)) {
        return self.$to_f()['$=='](other)
      } else {
        return other['$=='](self)
      }
    });
    
    $def(self, '$<=>', function $Rational_$lt_eq_gt$2(other) {
      var self = this, $ret_or_1 = nil;

      if ($eqeqeq($$$('Rational'), ($ret_or_1 = other))) {
        return $rb_minus($rb_times(self.num, other.$denominator()), $rb_times(self.den, other.$numerator()))['$<=>'](0)
      } else if ($eqeqeq($$$('Integer'), $ret_or_1)) {
        return $rb_minus(self.num, $rb_times(self.den, other))['$<=>'](0)
      } else if ($eqeqeq($$$('Float'), $ret_or_1)) {
        return self.$to_f()['$<=>'](other)
      } else {
        return self.$__coerced__("<=>", other)
      }
    });
    
    $def(self, '$+', function $Rational_$plus$3(other) {
      var self = this, $ret_or_1 = nil, num = nil, den = nil;

      if ($eqeqeq($$$('Rational'), ($ret_or_1 = other))) {
        
        num = $rb_plus($rb_times(self.num, other.$denominator()), $rb_times(self.den, other.$numerator()));
        den = $rb_times(self.den, other.$denominator());
        return $Kernel.$Rational(num, den);
      } else if ($eqeqeq($$$('Integer'), $ret_or_1)) {
        return $Kernel.$Rational($rb_plus(self.num, $rb_times(other, self.den)), self.den)
      } else if ($eqeqeq($$$('Float'), $ret_or_1)) {
        return $rb_plus(self.$to_f(), other)
      } else {
        return self.$__coerced__("+", other)
      }
    });
    
    $def(self, '$-', function $Rational_$minus$4(other) {
      var self = this, $ret_or_1 = nil, num = nil, den = nil;

      if ($eqeqeq($$$('Rational'), ($ret_or_1 = other))) {
        
        num = $rb_minus($rb_times(self.num, other.$denominator()), $rb_times(self.den, other.$numerator()));
        den = $rb_times(self.den, other.$denominator());
        return $Kernel.$Rational(num, den);
      } else if ($eqeqeq($$$('Integer'), $ret_or_1)) {
        return $Kernel.$Rational($rb_minus(self.num, $rb_times(other, self.den)), self.den)
      } else if ($eqeqeq($$$('Float'), $ret_or_1)) {
        return $rb_minus(self.$to_f(), other)
      } else {
        return self.$__coerced__("-", other)
      }
    });
    
    $def(self, '$*', function $Rational_$$5(other) {
      var self = this, $ret_or_1 = nil, num = nil, den = nil;

      if ($eqeqeq($$$('Rational'), ($ret_or_1 = other))) {
        
        num = $rb_times(self.num, other.$numerator());
        den = $rb_times(self.den, other.$denominator());
        return $Kernel.$Rational(num, den);
      } else if ($eqeqeq($$$('Integer'), $ret_or_1)) {
        return $Kernel.$Rational($rb_times(self.num, other), self.den)
      } else if ($eqeqeq($$$('Float'), $ret_or_1)) {
        return $rb_times(self.$to_f(), other)
      } else {
        return self.$__coerced__("*", other)
      }
    });
    
    $def(self, '$/', function $Rational_$slash$6(other) {
      var self = this, $ret_or_1 = nil, num = nil, den = nil;

      if ($eqeqeq($$$('Rational'), ($ret_or_1 = other))) {
        
        num = $rb_times(self.num, other.$denominator());
        den = $rb_times(self.den, other.$numerator());
        return $Kernel.$Rational(num, den);
      } else if ($eqeqeq($$$('Integer'), $ret_or_1)) {
        if ($eqeq(other, 0)) {
          return $rb_divide(self.$to_f(), 0.0)
        } else {
          return $Kernel.$Rational(self.num, $rb_times(self.den, other))
        }
      } else if ($eqeqeq($$$('Float'), $ret_or_1)) {
        return $rb_divide(self.$to_f(), other)
      } else {
        return self.$__coerced__("/", other)
      }
    });
    
    $def(self, '$**', function $Rational_$$$7(other) {
      var self = this, $ret_or_1 = nil;

      if ($eqeqeq($$$('Integer'), ($ret_or_1 = other))) {
        if (($eqeq(self, 0) && ($truthy($rb_lt(other, 0))))) {
          return $$$($$$('Float'), 'INFINITY')
        } else if ($truthy($rb_gt(other, 0))) {
          return $Kernel.$Rational(self.num['$**'](other), self.den['$**'](other))
        } else if ($truthy($rb_lt(other, 0))) {
          return $Kernel.$Rational(self.den['$**'](other['$-@']()), self.num['$**'](other['$-@']()))
        } else {
          return $Kernel.$Rational(1, 1)
        }
      } else if ($eqeqeq($$$('Float'), $ret_or_1)) {
        return self.$to_f()['$**'](other)
      } else if ($eqeqeq($$$('Rational'), $ret_or_1)) {
        if ($eqeq(other, 0)) {
          return $Kernel.$Rational(1, 1)
        } else if ($eqeq(other.$denominator(), 1)) {
          if ($truthy($rb_lt(other, 0))) {
            return $Kernel.$Rational(self.den['$**'](other.$numerator().$abs()), self.num['$**'](other.$numerator().$abs()))
          } else {
            return $Kernel.$Rational(self.num['$**'](other.$numerator()), self.den['$**'](other.$numerator()))
          }
        } else if (($eqeq(self, 0) && ($truthy($rb_lt(other, 0))))) {
          return $Kernel.$raise($$$('ZeroDivisionError'), "divided by 0")
        } else {
          return self.$to_f()['$**'](other)
        }
      } else {
        return self.$__coerced__("**", other)
      }
    });
    
    $def(self, '$abs', function $$abs() {
      var self = this;

      return $Kernel.$Rational(self.num.$abs(), self.den.$abs())
    });
    
    $def(self, '$ceil', function $$ceil(precision) {
      var self = this;

      
      if (precision == null) precision = 0;
      if ($eqeq(precision, 0)) {
        return $rb_divide(self.num['$-@'](), self.den)['$-@']().$ceil()
      } else {
        return self.$with_precision("ceil", precision)
      }    }, -1);
    
    $def(self, '$floor', function $$floor(precision) {
      var self = this;

      
      if (precision == null) precision = 0;
      if ($eqeq(precision, 0)) {
        return $rb_divide(self.num['$-@'](), self.den)['$-@']().$floor()
      } else {
        return self.$with_precision("floor", precision)
      }    }, -1);
    
    $def(self, '$hash', function $$hash() {
      var self = this;

      return "Rational:" + (self.num) + ":" + (self.den)
    });
    
    $def(self, '$inspect', function $$inspect() {
      var self = this;

      return "(" + (self) + ")"
    });
    
    $def(self, '$rationalize', function $$rationalize(eps) {
      var self = this;
      
      if (arguments.length > 1) {
        $Kernel.$raise($$$('ArgumentError'), "wrong number of arguments (" + (arguments.length) + " for 0..1)");
      }

      if (eps == null) {
        return self;
      }

      var e = eps.$abs(),
          a = $rb_minus(self, e),
          b = $rb_plus(self, e);

      var p0 = 0,
          p1 = 1,
          q0 = 1,
          q1 = 0,
          p2, q2;

      var c, k, t;

      while (true) {
        c = (a).$ceil();

        if ($rb_le(c, b)) {
          break;
        }

        k  = c - 1;
        p2 = k * p1 + p0;
        q2 = k * q1 + q0;
        t  = $rb_divide(1, $rb_minus(b, k));
        b  = $rb_divide(1, $rb_minus(a, k));
        a  = t;

        p0 = p1;
        q0 = q1;
        p1 = p2;
        q1 = q2;
      }

      return $Kernel.$Rational(c * p1 + p0, c * q1 + q0);
    }, -1);
    
    $def(self, '$round', function $$round(precision) {
      var self = this, num = nil, den = nil, approx = nil;

      
      if (precision == null) precision = 0;
      if (!$eqeq(precision, 0)) {
        return self.$with_precision("round", precision)
      }      if ($eqeq(self.num, 0)) {
        return 0
      }      if ($eqeq(self.den, 1)) {
        return self.num
      }      num = $rb_plus($rb_times(self.num.$abs(), 2), self.den);
      den = $rb_times(self.den, 2);
      approx = $rb_divide(num, den).$truncate();
      if ($truthy($rb_lt(self.num, 0))) {
        return approx['$-@']()
      } else {
        return approx
      }    }, -1);
    
    $def(self, '$to_f', function $$to_f() {
      var self = this;

      return $rb_divide(self.num, self.den)
    });
    
    $def(self, '$to_i', function $$to_i() {
      var self = this;

      return self.$truncate()
    });
    
    $def(self, '$to_r', $return_self);
    
    $def(self, '$to_s', function $$to_s() {
      var self = this;

      return "" + (self.num) + "/" + (self.den)
    });
    
    $def(self, '$truncate', function $$truncate(precision) {
      var self = this;

      
      if (precision == null) precision = 0;
      if ($eqeq(precision, 0)) {
        if ($truthy($rb_lt(self.num, 0))) {
          return self.$ceil()
        } else {
          return self.$floor()
        }
      } else {
        return self.$with_precision("truncate", precision)
      }    }, -1);
    
    $def(self, '$with_precision', function $$with_precision(method, precision) {
      var self = this, p = nil, s = nil;

      
      if (!$eqeqeq($$$('Integer'), precision)) {
        $Kernel.$raise($$$('TypeError'), "not an Integer");
      }      p = (10)['$**'](precision);
      s = $rb_times(self, p);
      if ($truthy($rb_lt(precision, 1))) {
        return $rb_divide(s.$send(method), p).$to_i()
      } else {
        return $Kernel.$Rational(s.$send(method), p)
      }    });
    $defs(self, '$from_string', function $$from_string(string) {
      
      
      var str = string.trimLeft(),
          re = /^[+-]?[\d_]+(\.[\d_]+)?/;
          str.match(re);
          var numerator, denominator;

      function isFloat() {
        return re.test(str);
      }

      function cutFloat() {
        var match = str.match(re);
        var number = match[0];
        str = str.slice(number.length);
        return number.replace(/_/g, '');
      }

      if (isFloat()) {
        numerator = parseFloat(cutFloat());

        if (str[0] === '/') {
          // rational real part
          str = str.slice(1);

          if (isFloat()) {
            denominator = parseFloat(cutFloat());
            return $Kernel.$Rational(numerator, denominator);
          } else {
            return $Kernel.$Rational(numerator, 1);
          }
        } else {
          return $Kernel.$Rational(numerator, 1);
        }
      } else {
        return $Kernel.$Rational(0, 1);
      }
    
    });
    $alias(self, "divide", "/");
    return $alias(self, "quo", "/");
  })('::', $$$('Numeric'));
};

Opal.modules["corelib/time"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $slice = Opal.slice, $deny_frozen_access = Opal.deny_frozen_access, $klass = Opal.klass, $Kernel = Opal.Kernel, $Opal = Opal.Opal, $defs = Opal.defs, $eqeqeq = Opal.eqeqeq, $def = Opal.def, $truthy = Opal.truthy, $rb_gt = Opal.rb_gt, $rb_lt = Opal.rb_lt, $send = Opal.send, $rb_plus = Opal.rb_plus, $rb_divide = Opal.rb_divide, $rb_minus = Opal.rb_minus, $range = Opal.range, $neqeq = Opal.neqeq, $rb_le = Opal.rb_le, $eqeq = Opal.eqeq, $alias = Opal.alias, self = Opal.top, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('require,include,===,raise,coerce_to!,respond_to?,to_str,to_i,_parse_offset,new,<=>,to_f,nil?,>,<,strftime,each,define_method,year,month,day,+,round,/,-,copy_instance_variables,initialize_dup,is_a?,zero?,wday,utc?,mon,yday,hour,min,sec,rjust,ljust,zone,to_s,[],cweek_cyear,jd,to_date,format,isdst,!=,<=,==,ceil,local,gm,asctime,getgm,gmt_offset,inspect,usec,gmtime,gmt?');
  
  self.$require("corelib/comparable");
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Time');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting);

    
    self.$include($$$('Comparable'));
    
    var days_of_week = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"],
        short_days   = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"],
        short_months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"],
        long_months  = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
    $defs(self, '$at', function $$at(seconds, frac) {
      
      var result;

      if ($$$('Time')['$==='](seconds)) {
        if (frac !== undefined) {
          $Kernel.$raise($$$('TypeError'), "can't convert Time into an exact number");
        }
        result = new Date(seconds.getTime());
        result.timezone = seconds.timezone;
        return result;
      }

      if (!seconds.$$is_number) {
        seconds = $Opal['$coerce_to!'](seconds, $$$('Integer'), "to_int");
      }

      if (frac === undefined) {
        return new Date(seconds * 1000);
      }

      if (!frac.$$is_number) {
        frac = $Opal['$coerce_to!'](frac, $$$('Integer'), "to_int");
      }

      return new Date(seconds * 1000 + (frac / 1000));
    }, -2);
    
    function time_params(year, month, day, hour, min, sec) {
      if (year.$$is_string) {
        year = parseInt(year, 10);
      } else {
        year = $Opal['$coerce_to!'](year, $$$('Integer'), "to_int");
      }

      if (month === nil) {
        month = 1;
      } else if (!month.$$is_number) {
        if ((month)['$respond_to?']("to_str")) {
          month = (month).$to_str();
          switch (month.toLowerCase()) {
          case 'jan': month =  1; break;
          case 'feb': month =  2; break;
          case 'mar': month =  3; break;
          case 'apr': month =  4; break;
          case 'may': month =  5; break;
          case 'jun': month =  6; break;
          case 'jul': month =  7; break;
          case 'aug': month =  8; break;
          case 'sep': month =  9; break;
          case 'oct': month = 10; break;
          case 'nov': month = 11; break;
          case 'dec': month = 12; break;
          default: month = (month).$to_i();
          }
        } else {
          month = $Opal['$coerce_to!'](month, $$$('Integer'), "to_int");
        }
      }

      if (month < 1 || month > 12) {
        $Kernel.$raise($$$('ArgumentError'), "month out of range: " + (month));
      }
      month = month - 1;

      if (day === nil) {
        day = 1;
      } else if (day.$$is_string) {
        day = parseInt(day, 10);
      } else {
        day = $Opal['$coerce_to!'](day, $$$('Integer'), "to_int");
      }

      if (day < 1 || day > 31) {
        $Kernel.$raise($$$('ArgumentError'), "day out of range: " + (day));
      }

      if (hour === nil) {
        hour = 0;
      } else if (hour.$$is_string) {
        hour = parseInt(hour, 10);
      } else {
        hour = $Opal['$coerce_to!'](hour, $$$('Integer'), "to_int");
      }

      if (hour < 0 || hour > 24) {
        $Kernel.$raise($$$('ArgumentError'), "hour out of range: " + (hour));
      }

      if (min === nil) {
        min = 0;
      } else if (min.$$is_string) {
        min = parseInt(min, 10);
      } else {
        min = $Opal['$coerce_to!'](min, $$$('Integer'), "to_int");
      }

      if (min < 0 || min > 59) {
        $Kernel.$raise($$$('ArgumentError'), "min out of range: " + (min));
      }

      if (sec === nil) {
        sec = 0;
      } else if (!sec.$$is_number) {
        if (sec.$$is_string) {
          sec = parseInt(sec, 10);
        } else {
          sec = $Opal['$coerce_to!'](sec, $$$('Integer'), "to_int");
        }
      }

      if (sec < 0 || sec > 60) {
        $Kernel.$raise($$$('ArgumentError'), "sec out of range: " + (sec));
      }

      return [year, month, day, hour, min, sec];
    }
    $defs(self, '$new', function $Time_new$1(year, month, day, hour, min, sec, utc_offset) {
      var self = this;
      if (month == null) month = nil;
      if (day == null) day = nil;
      if (hour == null) hour = nil;
      if (min == null) min = nil;
      if (sec == null) sec = nil;
      if (utc_offset == null) utc_offset = nil;
      
      var args, result, timezone, utc_date;

      if (year === undefined) {
        return new Date();
      }

      args  = time_params(year, month, day, hour, min, sec);
      year  = args[0];
      month = args[1];
      day   = args[2];
      hour  = args[3];
      min   = args[4];
      sec   = args[5];

      if (utc_offset === nil) {
        result = new Date(year, month, day, hour, min, 0, sec * 1000);
        if (year < 100) {
          result.setFullYear(year);
        }
        return result;
      }

      timezone = self.$_parse_offset(utc_offset);
      utc_date = new Date(Date.UTC(year, month, day, hour, min, 0, sec * 1000));
      if (year < 100) {
        utc_date.setUTCFullYear(year);
      }

      result = new Date(utc_date.getTime() - timezone * 3600000);
      result.timezone = timezone;

      return result;
    }, -1);
    $defs(self, '$_parse_offset', function $$_parse_offset(utc_offset) {
      
      
      var timezone;
      if (utc_offset.$$is_string) {
        if (utc_offset == 'UTC') {
          timezone = 0;
        }
        else if(/^[+-]\d\d:[0-5]\d$/.test(utc_offset)) {
          var sign, hours, minutes;
          sign = utc_offset[0];
          hours = +(utc_offset[1] + utc_offset[2]);
          minutes = +(utc_offset[4] + utc_offset[5]);

          timezone = (sign == '-' ? -1 : 1) * (hours + minutes / 60);
        }
        else {
          // Unsupported: "A".."I","K".."Z"
          $Kernel.$raise($$$('ArgumentError'), "\"+HH:MM\", \"-HH:MM\", \"UTC\" expected for utc_offset: " + (utc_offset));
        }
      }
      else if (utc_offset.$$is_number) {
        timezone = utc_offset / 3600;
      }
      else {
        $Kernel.$raise($$$('ArgumentError'), "Opal doesn't support other types for a timezone argument than Integer and String");
      }
      return timezone;
    
    });
    $defs(self, '$local', function $$local(year, month, day, hour, min, sec, millisecond, _dummy1, _dummy2, _dummy3) {
      
      
      if (month == null) month = nil;
      if (day == null) day = nil;
      if (hour == null) hour = nil;
      if (min == null) min = nil;
      if (sec == null) sec = nil;
      if (millisecond == null) millisecond = nil;
      if (_dummy1 == null) _dummy1 = nil;
      if (_dummy2 == null) _dummy2 = nil;
      if (_dummy3 == null) _dummy3 = nil;
      
      var args, result;

      if (arguments.length === 10) {
        args  = $slice(arguments);
        year  = args[5];
        month = args[4];
        day   = args[3];
        hour  = args[2];
        min   = args[1];
        sec   = args[0];
      }

      args  = time_params(year, month, day, hour, min, sec);
      year  = args[0];
      month = args[1];
      day   = args[2];
      hour  = args[3];
      min   = args[4];
      sec   = args[5];

      result = new Date(year, month, day, hour, min, 0, sec * 1000);
      if (year < 100) {
        result.setFullYear(year);
      }
      return result;
    }, -2);
    $defs(self, '$gm', function $$gm(year, month, day, hour, min, sec, millisecond, _dummy1, _dummy2, _dummy3) {
      
      
      if (month == null) month = nil;
      if (day == null) day = nil;
      if (hour == null) hour = nil;
      if (min == null) min = nil;
      if (sec == null) sec = nil;
      if (millisecond == null) millisecond = nil;
      if (_dummy1 == null) _dummy1 = nil;
      if (_dummy2 == null) _dummy2 = nil;
      if (_dummy3 == null) _dummy3 = nil;
      
      var args, result;

      if (arguments.length === 10) {
        args  = $slice(arguments);
        year  = args[5];
        month = args[4];
        day   = args[3];
        hour  = args[2];
        min   = args[1];
        sec   = args[0];
      }

      args  = time_params(year, month, day, hour, min, sec);
      year  = args[0];
      month = args[1];
      day   = args[2];
      hour  = args[3];
      min   = args[4];
      sec   = args[5];

      result = new Date(Date.UTC(year, month, day, hour, min, 0, sec * 1000));
      if (year < 100) {
        result.setUTCFullYear(year);
      }
      result.timezone = 0;
      return result;
    }, -2);
    $defs(self, '$now', function $$now() {
      var self = this;

      return self.$new()
    });
    
    $def(self, '$+', function $Time_$plus$2(other) {
      var self = this;

      
      if ($eqeqeq($$$('Time'), other)) {
        $Kernel.$raise($$$('TypeError'), "time + time?");
      }      
      if (!other.$$is_number) {
        other = $Opal['$coerce_to!'](other, $$$('Integer'), "to_int");
      }
      var result = new Date(self.getTime() + (other * 1000));
      result.timezone = self.timezone;
      return result;
    });
    
    $def(self, '$-', function $Time_$minus$3(other) {
      var self = this;

      
      if ($eqeqeq($$$('Time'), other)) {
        return (self.getTime() - other.getTime()) / 1000
      }      
      if (!other.$$is_number) {
        other = $Opal['$coerce_to!'](other, $$$('Integer'), "to_int");
      }
      var result = new Date(self.getTime() - (other * 1000));
      result.timezone = self.timezone;
      return result;
    });
    
    $def(self, '$<=>', function $Time_$lt_eq_gt$4(other) {
      var self = this, r = nil;

      if ($eqeqeq($$$('Time'), other)) {
        return self.$to_f()['$<=>'](other.$to_f())
      } else {
        
        r = other['$<=>'](self);
        if ($truthy(r['$nil?']())) {
          return nil
        } else if ($truthy($rb_gt(r, 0))) {
          return -1
        } else if ($truthy($rb_lt(r, 0))) {
          return 1
        } else {
          return 0
        }      }
    });
    
    $def(self, '$==', function $Time_$eq_eq$5(other) {
      var self = this, $ret_or_1 = nil;

      if ($truthy(($ret_or_1 = $$$('Time')['$==='](other)))) {
        return self.$to_f() === other.$to_f()
      } else {
        return $ret_or_1
      }
    });
    
    $def(self, '$asctime', function $$asctime() {
      var self = this;

      return self.$strftime("%a %b %e %H:%M:%S %Y")
    });
    $send([["year", "getFullYear", "getUTCFullYear"], ["mon", "getMonth", "getUTCMonth", 1], ["wday", "getDay", "getUTCDay"], ["day", "getDate", "getUTCDate"], ["hour", "getHours", "getUTCHours"], ["min", "getMinutes", "getUTCMinutes"], ["sec", "getSeconds", "getUTCSeconds"]], 'each', [], function $Time$6(method, getter, utcgetter, difference){var self = $Time$6.$$s == null ? this : $Time$6.$$s;

      
      if (method == null) method = nil;
      if (getter == null) getter = nil;
      if (utcgetter == null) utcgetter = nil;
      if (difference == null) difference = 0;
      return $send(self, 'define_method', [method], function $$7(){var self = $$7.$$s == null ? this : $$7.$$s;

        
        return difference + ((self.timezone != null) ?
          (new Date(self.getTime() + self.timezone * 3600000))[utcgetter]() :
          self[getter]())
      }, {$$s: self});}, {$$arity: -4, $$s: self});
    
    $def(self, '$yday', function $$yday() {
      var self = this, start_of_year = nil, start_of_day = nil, one_day = nil;

      
      start_of_year = $$('Time').$new(self.$year()).$to_i();
      start_of_day = $$('Time').$new(self.$year(), self.$month(), self.$day()).$to_i();
      one_day = 86400;
      return $rb_plus($rb_divide($rb_minus(start_of_day, start_of_year), one_day).$round(), 1);
    });
    
    $def(self, '$isdst', function $$isdst() {
      var self = this;

      
      var jan = new Date(self.getFullYear(), 0, 1),
          jul = new Date(self.getFullYear(), 6, 1);
      return self.getTimezoneOffset() < Math.max(jan.getTimezoneOffset(), jul.getTimezoneOffset());
    
    });
    
    $def(self, '$dup', function $$dup() {
      var self = this, copy = nil;

      
      copy = new Date(self.getTime());
      copy.$copy_instance_variables(self);
      copy.$initialize_dup(self);
      return copy;
    });
    
    $def(self, '$eql?', function $Time_eql$ques$8(other) {
      var self = this, $ret_or_1 = nil;

      if ($truthy(($ret_or_1 = other['$is_a?']($$$('Time'))))) {
        return self['$<=>'](other)['$zero?']()
      } else {
        return $ret_or_1
      }
    });
    $send([["sunday?", 0], ["monday?", 1], ["tuesday?", 2], ["wednesday?", 3], ["thursday?", 4], ["friday?", 5], ["saturday?", 6]], 'each', [], function $Time$9(method, weekday){var self = $Time$9.$$s == null ? this : $Time$9.$$s;

      
      if (method == null) method = nil;
      if (weekday == null) weekday = nil;
      return $send(self, 'define_method', [method], function $$10(){var self = $$10.$$s == null ? this : $$10.$$s;

        return self.$wday() === weekday}, {$$s: self});}, {$$s: self});
    
    $def(self, '$hash', function $$hash() {
      var self = this;

      return 'Time:' + self.getTime();
    });
    
    $def(self, '$inspect', function $$inspect() {
      var self = this;

      if ($truthy(self['$utc?']())) {
        return self.$strftime("%Y-%m-%d %H:%M:%S UTC")
      } else {
        return self.$strftime("%Y-%m-%d %H:%M:%S %z")
      }
    });
    
    $def(self, '$succ', function $$succ() {
      var self = this;

      
      var result = new Date(self.getTime() + 1000);
      result.timezone = self.timezone;
      return result;
    
    });
    
    $def(self, '$usec', function $$usec() {
      var self = this;

      return self.getMilliseconds() * 1000;
    });
    
    $def(self, '$zone', function $$zone() {
      var self = this;

      
      if (self.timezone === 0) return "UTC";
      else if (self.timezone != null) return nil;

      var string = self.toString(),
          result;

      if (string.indexOf('(') == -1) {
        result = string.match(/[A-Z]{3,4}/)[0];
      }
      else {
        result = string.match(/\((.+)\)(?:\s|$)/)[1];
      }

      if (result == "GMT" && /(GMT\W*\d{4})/.test(string)) {
        return RegExp.$1;
      }
      else {
        return result;
      }
    
    });
    
    $def(self, '$getgm', function $$getgm() {
      var self = this;

      
      var result = new Date(self.getTime());
      result.timezone = 0;
      return result;
    
    });
    
    $def(self, '$gmtime', function $$gmtime() {
      var self = this;

      
      if (self.timezone !== 0) {
        $deny_frozen_access(self);
        self.timezone = 0;
      }
      return self;
    
    });
    
    $def(self, '$gmt?', function $Time_gmt$ques$11() {
      var self = this;

      return self.timezone === 0;
    });
    
    $def(self, '$gmt_offset', function $$gmt_offset() {
      var self = this;

      return (self.timezone != null) ? self.timezone * 60 : -self.getTimezoneOffset() * 60;
    });
    
    $def(self, '$strftime', function $$strftime(format) {
      var self = this;

      
      return format.replace(/%([\-_#^0]*:{0,2})(\d+)?([EO]*)(.)/g, function(full, flags, width, _, conv) {
        var result = "", jd, c, s,
            zero   = flags.indexOf('0') !== -1,
            pad    = flags.indexOf('-') === -1,
            blank  = flags.indexOf('_') !== -1,
            upcase = flags.indexOf('^') !== -1,
            invert = flags.indexOf('#') !== -1,
            colons = (flags.match(':') || []).length;

        width = parseInt(width, 10);

        if (zero && blank) {
          if (flags.indexOf('0') < flags.indexOf('_')) {
            zero = false;
          }
          else {
            blank = false;
          }
        }

        switch (conv) {
          case 'Y':
            result += self.$year();
            break;

          case 'C':
            zero    = !blank;
            result += Math.round(self.$year() / 100);
            break;

          case 'y':
            zero    = !blank;
            result += (self.$year() % 100);
            break;

          case 'm':
            zero    = !blank;
            result += self.$mon();
            break;

          case 'B':
            result += long_months[self.$mon() - 1];
            break;

          case 'b':
          case 'h':
            blank   = !zero;
            result += short_months[self.$mon() - 1];
            break;

          case 'd':
            zero    = !blank;
            result += self.$day();
            break;

          case 'e':
            blank   = !zero;
            result += self.$day();
            break;

          case 'j':
            zero    = !blank;
            width   = isNaN(width) ? 3 : width;
            result += self.$yday();
            break;

          case 'H':
            zero    = !blank;
            result += self.$hour();
            break;

          case 'k':
            blank   = !zero;
            result += self.$hour();
            break;

          case 'I':
            zero    = !blank;
            result += (self.$hour() % 12 || 12);
            break;

          case 'l':
            blank   = !zero;
            result += (self.$hour() % 12 || 12);
            break;

          case 'P':
            result += (self.$hour() >= 12 ? "pm" : "am");
            break;

          case 'p':
            result += (self.$hour() >= 12 ? "PM" : "AM");
            break;

          case 'M':
            zero    = !blank;
            result += self.$min();
            break;

          case 'S':
            zero    = !blank;
            result += self.$sec();
            break;

          case 'L':
            zero    = !blank;
            width   = isNaN(width) ? 3 : width;
            result += self.getMilliseconds();
            break;

          case 'N':
            width   = isNaN(width) ? 9 : width;
            result += (self.getMilliseconds().toString()).$rjust(3, "0");
            result  = (result).$ljust(width, "0");
            break;

          case 'z':
            var offset  = (self.timezone == null) ? self.getTimezoneOffset() : (-self.timezone * 60),
                hours   = Math.floor(Math.abs(offset) / 60),
                minutes = Math.abs(offset) % 60;

            result += offset < 0 ? "+" : "-";
            result += hours < 10 ? "0" : "";
            result += hours;

            if (colons > 0) {
              result += ":";
            }

            result += minutes < 10 ? "0" : "";
            result += minutes;

            if (colons > 1) {
              result += ":00";
            }

            break;

          case 'Z':
            result += self.$zone();
            break;

          case 'A':
            result += days_of_week[self.$wday()];
            break;

          case 'a':
            result += short_days[self.$wday()];
            break;

          case 'u':
            result += (self.$wday() + 1);
            break;

          case 'w':
            result += self.$wday();
            break;

          case 'V':
            result += self.$cweek_cyear()['$[]'](0).$to_s().$rjust(2, "0");
            break;

          case 'G':
            result += self.$cweek_cyear()['$[]'](1);
            break;

          case 'g':
            result += self.$cweek_cyear()['$[]'](1)['$[]']($range(-2, -1, false));
            break;

          case 's':
            result += self.$to_i();
            break;

          case 'n':
            result += "\n";
            break;

          case 't':
            result += "\t";
            break;

          case '%':
            result += "%";
            break;

          case 'c':
            result += self.$strftime("%a %b %e %T %Y");
            break;

          case 'D':
          case 'x':
            result += self.$strftime("%m/%d/%y");
            break;

          case 'F':
            result += self.$strftime("%Y-%m-%d");
            break;

          case 'v':
            result += self.$strftime("%e-%^b-%4Y");
            break;

          case 'r':
            result += self.$strftime("%I:%M:%S %p");
            break;

          case 'R':
            result += self.$strftime("%H:%M");
            break;

          case 'T':
          case 'X':
            result += self.$strftime("%H:%M:%S");
            break;

          // Non-standard: JIS X 0301 date format
          case 'J':
            jd = self.$to_date().$jd();
            if (jd < 2405160) {
              result += self.$strftime("%Y-%m-%d");
              break;
            }
            else if (jd < 2419614)
              c = 'M', s = 1867;
            else if (jd < 2424875)
              c = 'T', s = 1911;
            else if (jd < 2447535)
              c = 'S', s = 1925;
            else if (jd < 2458605)
              c = 'H', s = 1988;
            else
              c = 'R', s = 2018;

            result += self.$format("%c%02d", c, $rb_minus(self.$year(), s));
            result += self.$strftime("-%m-%d");
            break;

          default:
            return full;
        }

        if (upcase) {
          result = result.toUpperCase();
        }

        if (invert) {
          result = result.replace(/[A-Z]/, function(c) { c.toLowerCase(); }).
                          replace(/[a-z]/, function(c) { c.toUpperCase(); });
        }

        if (pad && (zero || blank)) {
          result = (result).$rjust(isNaN(width) ? 2 : width, blank ? " " : "0");
        }

        return result;
      });
    
    });
    
    $def(self, '$to_a', function $$to_a() {
      var self = this;

      return [self.$sec(), self.$min(), self.$hour(), self.$day(), self.$month(), self.$year(), self.$wday(), self.$yday(), self.$isdst(), self.$zone()]
    });
    
    $def(self, '$to_f', function $$to_f() {
      var self = this;

      return self.getTime() / 1000;
    });
    
    $def(self, '$to_i', function $$to_i() {
      var self = this;

      return parseInt(self.getTime() / 1000, 10);
    });
    
    $def(self, '$cweek_cyear', function $$cweek_cyear() {
      var self = this, jan01 = nil, jan01_wday = nil, year = nil, offset = nil, week = nil, dec31 = nil, dec31_wday = nil;

      
      jan01 = $$$('Time').$new(self.$year(), 1, 1);
      jan01_wday = jan01.$wday();
      year = self.$year();
      if (($truthy($rb_le(jan01_wday, 4)) && ($neqeq(jan01_wday, 0)))) {
        offset = $rb_minus(jan01_wday, 1);
      } else {
        
        offset = $rb_minus($rb_minus(jan01_wday, 7), 1);
        if ($eqeq(offset, -8)) {
          offset = -1;
        }      }      week = $rb_divide($rb_plus(self.$yday(), offset), 7.0).$ceil();
      if ($truthy($rb_le(week, 0))) {
        return $$$('Time').$new($rb_minus(self.$year(), 1), 12, 31).$cweek_cyear()
      } else if ($eqeq(week, 53)) {
        
        dec31 = $$$('Time').$new(self.$year(), 12, 31);
        dec31_wday = dec31.$wday();
        if (($truthy($rb_le(dec31_wday, 3)) && ($neqeq(dec31_wday, 0)))) {
          
          week = 1;
          year = $rb_plus(year, 1);
        }      }      return [week, year];
    });
    (function(self, $parent_nesting) {
      
      
      $alias(self, "mktime", "local");
      return $alias(self, "utc", "gm");
    })(Opal.get_singleton_class(self));
    $alias(self, "ctime", "asctime");
    $alias(self, "dst?", "isdst");
    $alias(self, "getutc", "getgm");
    $alias(self, "gmtoff", "gmt_offset");
    $alias(self, "mday", "day");
    $alias(self, "month", "mon");
    $alias(self, "to_s", "inspect");
    $alias(self, "tv_sec", "to_i");
    $alias(self, "tv_usec", "usec");
    $alias(self, "utc", "gmtime");
    $alias(self, "utc?", "gmt?");
    return $alias(self, "utc_offset", "gmt_offset");
  })('::', Date, $nesting);
};

Opal.modules["corelib/struct"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $slice = Opal.slice, $extract_kwargs = Opal.extract_kwargs, $ensure_kwargs = Opal.ensure_kwargs, $truthy = Opal.truthy, $neqeq = Opal.neqeq, $eqeq = Opal.eqeq, $Opal = Opal.Opal, $send = Opal.send, $Class = Opal.Class, $to_a = Opal.to_a, $def = Opal.def, $defs = Opal.defs, $Kernel = Opal.Kernel, $hash2 = Opal.hash2, $rb_gt = Opal.rb_gt, $rb_minus = Opal.rb_minus, $eqeqeq = Opal.eqeqeq, $rb_lt = Opal.rb_lt, $rb_ge = Opal.rb_ge, $rb_plus = Opal.rb_plus, $alias = Opal.alias, self = Opal.top, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('require,include,!=,upcase,[],==,class,unshift,const_name!,map,coerce_to!,new,each,define_struct_attribute,allocate,initialize,alias_method,module_eval,to_proc,const_set,raise,<<,members,define_method,instance_eval,last,>,length,-,keys,any?,join,[]=,each_with_index,hash,===,<,-@,size,>=,include?,to_sym,instance_of?,__id__,eql?,enum_for,+,name,each_pair,inspect,to_h,args,each_with_object,flatten,to_a,respond_to?,dig');
  
  self.$require("corelib/enumerable");
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Struct');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting);

    
    self.$include($$$('Enumerable'));
    $defs(self, '$new', function $Struct_new$1(const_name, $a, $b) {
      var block = $Struct_new$1.$$p || nil, $post_args, $kwargs, args, keyword_init, self = this, klass = nil;

      $Struct_new$1.$$p = null;
      $post_args = $slice(arguments, 1);
      $kwargs = $extract_kwargs($post_args);
      $kwargs = $ensure_kwargs($kwargs);
      args = $post_args;
      
      keyword_init = $kwargs.$$smap["keyword_init"];if (keyword_init == null) keyword_init = false;
      if ($truthy(const_name)) {
        if (($eqeq(const_name.$class(), $$$('String')) && ($neqeq(const_name['$[]'](0).$upcase(), const_name['$[]'](0))))) {
          
          args.$unshift(const_name);
          const_name = nil;
        } else {
          
          try {
            const_name = $Opal['$const_name!'](const_name);
          } catch ($err) {
            if (Opal.rescue($err, [$$$('TypeError'), $$$('NameError')])) {
              try {
                
                args.$unshift(const_name);
                const_name = nil;
              } finally { Opal.pop_exception(); }
            } else { throw $err; }
          }        }
      }      $send(args, 'map', [], function $$2(arg){
        
        if (arg == null) arg = nil;
        return $Opal['$coerce_to!'](arg, $$$('String'), "to_str");});
      klass = $send($Class, 'new', [self], function $$3(){var self = $$3.$$s == null ? this : $$3.$$s;

        
        $send(args, 'each', [], function $$4(arg){var self = $$4.$$s == null ? this : $$4.$$s;

          
          if (arg == null) arg = nil;
          return self.$define_struct_attribute(arg);}, {$$s: self});
        return (function(self, $parent_nesting) {
          
          
          
          $def(self, '$new', function $new$5($a) {
            var $post_args, args, self = this, instance = nil;

            
            $post_args = $slice(arguments);
            args = $post_args;
            instance = self.$allocate();
            instance.$$data = {};
            $send(instance, 'initialize', $to_a(args));
            return instance;
          }, -1);
          return self.$alias_method("[]", "new");
        })(Opal.get_singleton_class(self));}, {$$s: self});
      if ($truthy(block)) {
        $send(klass, 'module_eval', [], block.$to_proc());
      }      klass.$$keyword_init = keyword_init;
      if ($truthy(const_name)) {
        $$$('Struct').$const_set(const_name, klass);
      }      return klass;
    }, -2);
    $defs(self, '$define_struct_attribute', function $$define_struct_attribute(name) {
      var self = this;

      
      if ($eqeq(self, $$$('Struct'))) {
        $Kernel.$raise($$$('ArgumentError'), "you cannot define attributes to the Struct class");
      }      self.$members()['$<<'](name);
      $send(self, 'define_method', [name], function $$6(){var self = $$6.$$s == null ? this : $$6.$$s;

        return self.$$data[name];}, {$$s: self});
      return $send(self, 'define_method', ["" + (name) + "="], function $$7(value){var self = $$7.$$s == null ? this : $$7.$$s;

        
        if (value == null) value = nil;
        return self.$$data[name] = value;}, {$$s: self});
    });
    $defs(self, '$members', function $$members() {
      var self = this, $ret_or_1 = nil;
      if (self.members == null) self.members = nil;

      
      if ($eqeq(self, $$$('Struct'))) {
        $Kernel.$raise($$$('ArgumentError'), "the Struct class has no members");
      }      return (self.members = ($truthy(($ret_or_1 = self.members)) ? ($ret_or_1) : ([])));
    });
    $defs(self, '$inherited', function $$inherited(klass) {
      var self = this, members = nil;
      if (self.members == null) self.members = nil;

      
      members = self.members;
      return $send(klass, 'instance_eval', [], function $$8(){var self = $$8.$$s == null ? this : $$8.$$s;

        return (self.members = members)}, {$$s: self});
    });
    
    $def(self, '$initialize', function $$initialize($a) {
      var $post_args, args, self = this, kwargs = nil, $ret_or_1 = nil, extra = nil;

      
      $post_args = $slice(arguments);
      args = $post_args;
      if ($truthy(self.$class().$$keyword_init)) {
        
        kwargs = ($truthy(($ret_or_1 = args.$last())) ? ($ret_or_1) : ($hash2([], {})));
        if (($truthy($rb_gt(args.$length(), 1)) || ($truthy((args.length === 1 && !kwargs.$$is_hash))))) {
          $Kernel.$raise($$$('ArgumentError'), "wrong number of arguments (given " + (args.$length()) + ", expected 0)");
        }        extra = $rb_minus(kwargs.$keys(), self.$class().$members());
        if ($truthy(extra['$any?']())) {
          $Kernel.$raise($$$('ArgumentError'), "unknown keywords: " + (extra.$join(", ")));
        }        return $send(self.$class().$members(), 'each', [], function $$9(name){var $b, self = $$9.$$s == null ? this : $$9.$$s;

          
          if (name == null) name = nil;
          return ($b = [name, kwargs['$[]'](name)], $send(self, '[]=', $b), $b[$b.length - 1]);}, {$$s: self});
      } else {
        
        if ($truthy($rb_gt(args.$length(), self.$class().$members().$length()))) {
          $Kernel.$raise($$$('ArgumentError'), "struct size differs");
        }        return $send(self.$class().$members(), 'each_with_index', [], function $$10(name, index){var $b, self = $$10.$$s == null ? this : $$10.$$s;

          
          if (name == null) name = nil;
          if (index == null) index = nil;
          return ($b = [name, args['$[]'](index)], $send(self, '[]=', $b), $b[$b.length - 1]);}, {$$s: self});
      }    }, -1);
    
    $def(self, '$initialize_copy', function $$initialize_copy(from) {
      var self = this;

      
      self.$$data = {};
      var keys = Object.keys(from.$$data), i, max, name;
      for (i = 0, max = keys.length; i < max; i++) {
        name = keys[i];
        self.$$data[name] = from.$$data[name];
      }
    
    });
    $defs(self, '$keyword_init?', function $Struct_keyword_init$ques$11() {
      var self = this;

      return self.$$keyword_init;
    });
    
    $def(self, '$members', function $$members() {
      var self = this;

      return self.$class().$members()
    });
    
    $def(self, '$hash', function $$hash() {
      var self = this;

      return $$('Hash').$new(self.$$data).$hash()
    });
    
    $def(self, '$[]', function $Struct_$$$12(name) {
      var self = this;

      
      if ($eqeqeq($$$('Integer'), name)) {
        
        if ($truthy($rb_lt(name, self.$class().$members().$size()['$-@']()))) {
          $Kernel.$raise($$$('IndexError'), "offset " + (name) + " too small for struct(size:" + (self.$class().$members().$size()) + ")");
        }        if ($truthy($rb_ge(name, self.$class().$members().$size()))) {
          $Kernel.$raise($$$('IndexError'), "offset " + (name) + " too large for struct(size:" + (self.$class().$members().$size()) + ")");
        }        name = self.$class().$members()['$[]'](name);
      } else if ($eqeqeq($$$('String'), name)) {
        
        if(!self.$$data.hasOwnProperty(name)) {
          $Kernel.$raise($$$('NameError').$new("no member '" + (name) + "' in struct", name));
        }
      
      } else {
        $Kernel.$raise($$$('TypeError'), "no implicit conversion of " + (name.$class()) + " into Integer");
      }      name = $Opal['$coerce_to!'](name, $$$('String'), "to_str");
      return self.$$data[name];    });
    
    $def(self, '$[]=', function $Struct_$$$eq$13(name, value) {
      var self = this;

      
      if ($eqeqeq($$$('Integer'), name)) {
        
        if ($truthy($rb_lt(name, self.$class().$members().$size()['$-@']()))) {
          $Kernel.$raise($$$('IndexError'), "offset " + (name) + " too small for struct(size:" + (self.$class().$members().$size()) + ")");
        }        if ($truthy($rb_ge(name, self.$class().$members().$size()))) {
          $Kernel.$raise($$$('IndexError'), "offset " + (name) + " too large for struct(size:" + (self.$class().$members().$size()) + ")");
        }        name = self.$class().$members()['$[]'](name);
      } else if ($eqeqeq($$$('String'), name)) {
        if (!$truthy(self.$class().$members()['$include?'](name.$to_sym()))) {
          $Kernel.$raise($$$('NameError').$new("no member '" + (name) + "' in struct", name));
        }
      } else {
        $Kernel.$raise($$$('TypeError'), "no implicit conversion of " + (name.$class()) + " into Integer");
      }      name = $Opal['$coerce_to!'](name, $$$('String'), "to_str");
      return self.$$data[name] = value;    });
    
    $def(self, '$==', function $Struct_$eq_eq$14(other) {
      var self = this;

      
      if (!$truthy(other['$instance_of?'](self.$class()))) {
        return false
      }      
      var recursed1 = {}, recursed2 = {};

      function _eqeq(struct, other) {
        var key, a, b;

        recursed1[(struct).$__id__()] = true;
        recursed2[(other).$__id__()] = true;

        for (key in struct.$$data) {
          a = struct.$$data[key];
          b = other.$$data[key];

          if ($$$('Struct')['$==='](a)) {
            if (!recursed1.hasOwnProperty((a).$__id__()) || !recursed2.hasOwnProperty((b).$__id__())) {
              if (!_eqeq(a, b)) {
                return false;
              }
            }
          } else {
            if (!(a)['$=='](b)) {
              return false;
            }
          }
        }

        return true;
      }

      return _eqeq(self, other);
    });
    
    $def(self, '$eql?', function $Struct_eql$ques$15(other) {
      var self = this;

      
      if (!$truthy(other['$instance_of?'](self.$class()))) {
        return false
      }      
      var recursed1 = {}, recursed2 = {};

      function _eqeq(struct, other) {
        var key, a, b;

        recursed1[(struct).$__id__()] = true;
        recursed2[(other).$__id__()] = true;

        for (key in struct.$$data) {
          a = struct.$$data[key];
          b = other.$$data[key];

          if ($$$('Struct')['$==='](a)) {
            if (!recursed1.hasOwnProperty((a).$__id__()) || !recursed2.hasOwnProperty((b).$__id__())) {
              if (!_eqeq(a, b)) {
                return false;
              }
            }
          } else {
            if (!(a)['$eql?'](b)) {
              return false;
            }
          }
        }

        return true;
      }

      return _eqeq(self, other);
    });
    
    $def(self, '$each', function $$each() {
      var $yield = $$each.$$p || nil, self = this;

      $$each.$$p = null;
      
      if (!($yield !== nil)) {
        return $send(self, 'enum_for', ["each"], function $$16(){var self = $$16.$$s == null ? this : $$16.$$s;

          return self.$size()}, {$$s: self})
      }      $send(self.$class().$members(), 'each', [], function $$17(name){var self = $$17.$$s == null ? this : $$17.$$s;

        
        if (name == null) name = nil;
        return Opal.yield1($yield, self['$[]'](name));}, {$$s: self});
      return self;
    });
    
    $def(self, '$each_pair', function $$each_pair() {
      var $yield = $$each_pair.$$p || nil, self = this;

      $$each_pair.$$p = null;
      
      if (!($yield !== nil)) {
        return $send(self, 'enum_for', ["each_pair"], function $$18(){var self = $$18.$$s == null ? this : $$18.$$s;

          return self.$size()}, {$$s: self})
      }      $send(self.$class().$members(), 'each', [], function $$19(name){var self = $$19.$$s == null ? this : $$19.$$s;

        
        if (name == null) name = nil;
        return Opal.yield1($yield, [name, self['$[]'](name)]);}, {$$s: self});
      return self;
    });
    
    $def(self, '$length', function $$length() {
      var self = this;

      return self.$class().$members().$length()
    });
    
    $def(self, '$to_a', function $$to_a() {
      var self = this;

      return $send(self.$class().$members(), 'map', [], function $$20(name){var self = $$20.$$s == null ? this : $$20.$$s;

        
        if (name == null) name = nil;
        return self['$[]'](name);}, {$$s: self})
    });
    var inspect_stack = [];
    
    $def(self, '$inspect', function $$inspect() {
      var self = this, result = nil, pushed = nil;

      return (function() { try {
      
      result = "#<struct ";
      if ($truthy((inspect_stack)['$include?'](self.$__id__()))) {
        return $rb_plus(result, ":...>")
      } else {
        
        (inspect_stack)['$<<'](self.$__id__());
        pushed = true;
        if (($eqeqeq($$$('Struct'), self) && ($truthy(self.$class().$name())))) {
          result = $rb_plus(result, "" + (self.$class()) + " ");
        };
        result = $rb_plus(result, $send(self.$each_pair(), 'map', [], function $$21(name, value){
          
          if (name == null) name = nil;
          if (value == null) value = nil;
          return "" + (name) + "=" + ($$('Opal').$inspect(value));}).$join(", "));
        result = $rb_plus(result, ">");
        return result;
      };
      } finally {
        ($truthy(pushed) ? (inspect_stack.pop()) : nil);
      } })()
    });
    
    $def(self, '$to_h', function $$to_h() {
      var block = $$to_h.$$p || nil, self = this;

      $$to_h.$$p = null;
      if ((block !== nil)) {
        return $send($send(self, 'map', [], block.$to_proc()), 'to_h', $to_a(self.$args()))
      }      return $send(self.$class().$members(), 'each_with_object', [$hash2([], {})], function $$22(name, h){var $a, self = $$22.$$s == null ? this : $$22.$$s;

        
        if (name == null) name = nil;
        if (h == null) h = nil;
        return ($a = [name, self['$[]'](name)], $send(h, '[]=', $a), $a[$a.length - 1]);}, {$$s: self});
    });
    
    $def(self, '$values_at', function $$values_at($a) {
      var $post_args, args, self = this;

      
      $post_args = $slice(arguments);
      args = $post_args;
      args = $send(args, 'map', [], function $$23(arg){
        
        if (arg == null) arg = nil;
        return arg.$$is_range ? arg.$to_a() : arg;}).$flatten();
      
      var result = [];
      for (var i = 0, len = args.length; i < len; i++) {
        if (!args[i].$$is_number) {
          $Kernel.$raise($$$('TypeError'), "no implicit conversion of " + ((args[i]).$class()) + " into Integer");
        }
        result.push(self['$[]'](args[i]));
      }
      return result;
    }, -1);
    
    $def(self, '$dig', function $$dig(key, $a) {
      var $post_args, keys, self = this, item = nil;

      
      $post_args = $slice(arguments, 1);
      keys = $post_args;
      item = ($truthy(key.$$is_string && self.$$data.hasOwnProperty(key)) ? (self.$$data[key] || nil) : nil);
      
      if (item === nil || keys.length === 0) {
        return item;
      }
      if (!$truthy(item['$respond_to?']("dig"))) {
        $Kernel.$raise($$$('TypeError'), "" + (item.$class()) + " does not have #dig method");
      }      return $send(item, 'dig', $to_a(keys));
    }, -2);
    $alias(self, "size", "length");
    $alias(self, "to_s", "inspect");
    return $alias(self, "values", "to_a");
  })('::', null, $nesting);
};

Opal.modules["corelib/set"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $freeze = Opal.freeze, $klass = Opal.klass, $slice = Opal.slice, $defs = Opal.defs, $hash2 = Opal.hash2, $truthy = Opal.truthy, $eqeqeq = Opal.eqeqeq, $Kernel = Opal.Kernel, $send = Opal.send, $def = Opal.def, $eqeq = Opal.eqeq, $rb_lt = Opal.rb_lt, $rb_le = Opal.rb_le, $alias = Opal.alias, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('include,new,nil?,===,raise,each,add,merge,class,respond_to?,subtract,dup,join,to_a,equal?,instance_of?,==,instance_variable_get,size,is_a?,all?,include?,[]=,enum_for,[],<<,replace,compare_by_identity,name,compare_by_identity?,delete,select,frozen?,freeze,reject,delete_if,to_proc,keep_if,each_key,empty?,eql?,instance_eval,clear,<,<=,any?,!,intersect?,keys,|,proper_subset?,subset?,proper_superset?,superset?,-,select!,collect!');
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Set');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting), $ret_or_1 = nil, $proto = self.$$prototype;

    $proto.hash = nil;
    
    self.$include($$$('Enumerable'));
    $defs(self, '$[]', function $Set_$$$1($a) {
      var $post_args, ary, self = this;

      
      $post_args = $slice(arguments);
      ary = $post_args;
      return self.$new(ary);
    }, -1);
    
    $def(self, '$initialize', function $$initialize(enum$) {
      var block = $$initialize.$$p || nil, self = this;

      $$initialize.$$p = null;
      if (enum$ == null) enum$ = nil;
      self.hash = $hash2([], {});
      if ($truthy(enum$['$nil?']())) {
        return nil
      }      if (!$eqeqeq($$$('Enumerable'), enum$)) {
        $Kernel.$raise($$$('ArgumentError'), "value must be enumerable");
      }      if ($truthy(block)) {
        return $send(enum$, 'each', [], function $$2(item){var self = $$2.$$s == null ? this : $$2.$$s;

          
          if (item == null) item = nil;
          return self.$add(Opal.yield1(block, item));}, {$$s: self})
      } else {
        return self.$merge(enum$)
      }    }, -1);
    
    $def(self, '$dup', function $$dup() {
      var self = this, result = nil;

      
      result = self.$class().$new();
      return result.$merge(self);
    });
    
    $def(self, '$-', function $Set_$minus$3(enum$) {
      var self = this;

      
      if (!$truthy(enum$['$respond_to?']("each"))) {
        $Kernel.$raise($$$('ArgumentError'), "value must be enumerable");
      }      return self.$dup().$subtract(enum$);
    });
    
    $def(self, '$inspect', function $$inspect() {
      var self = this;

      return "#<Set: {" + (self.$to_a().$join(",")) + "}>"
    });
    
    $def(self, '$==', function $Set_$eq_eq$4(other) {
      var self = this;

      if ($truthy(self['$equal?'](other))) {
        return true
      } else if ($truthy(other['$instance_of?'](self.$class()))) {
        return self.hash['$=='](other.$instance_variable_get("@hash"))
      } else if (($truthy(other['$is_a?']($$$('Set'))) && ($eqeq(self.$size(), other.$size())))) {
        return $send(other, 'all?', [], function $$5(o){var self = $$5.$$s == null ? this : $$5.$$s;
          if (self.hash == null) self.hash = nil;

          
          if (o == null) o = nil;
          return self.hash['$include?'](o);}, {$$s: self})
      } else {
        return false
      }
    });
    
    $def(self, '$add', function $$add(o) {
      var self = this;

      
      self.hash['$[]='](o, true);
      return self;
    });
    
    $def(self, '$classify', function $$classify() {
      var block = $$classify.$$p || nil, self = this, result = nil;

      $$classify.$$p = null;
      if (!(block !== nil)) {
        return self.$enum_for("classify")
      }      result = $send($$$('Hash'), 'new', [], function $$6(h, k){var $a, self = $$6.$$s == null ? this : $$6.$$s;

        
        if (h == null) h = nil;
        if (k == null) k = nil;
        return ($a = [k, self.$class().$new()], $send(h, '[]=', $a), $a[$a.length - 1]);}, {$$s: self});
      $send(self, 'each', [], function $$7(item){
        
        if (item == null) item = nil;
        return result['$[]'](Opal.yield1(block, item)).$add(item);});
      return result;
    });
    
    $def(self, '$collect!', function $Set_collect$excl$8() {
      var block = $Set_collect$excl$8.$$p || nil, self = this, result = nil;

      $Set_collect$excl$8.$$p = null;
      if (!(block !== nil)) {
        return self.$enum_for("collect!")
      }      result = self.$class().$new();
      $send(self, 'each', [], function $$9(item){
        
        if (item == null) item = nil;
        return result['$<<'](Opal.yield1(block, item));});
      return self.$replace(result);
    });
    
    $def(self, '$compare_by_identity', function $$compare_by_identity() {
      var self = this;

      if ($truthy(self.hash['$respond_to?']("compare_by_identity"))) {
        
        self.hash.$compare_by_identity();
        return self;
      } else {
        return self.$raise($$('NotImplementedError'), "" + (self.$class().$name()) + "#" + ("compare_by_identity") + " is not implemented")
      }
    });
    
    $def(self, '$compare_by_identity?', function $Set_compare_by_identity$ques$10() {
      var self = this, $ret_or_1 = nil;

      if ($truthy(($ret_or_1 = self.hash['$respond_to?']("compare_by_identity?")))) {
        return self.hash['$compare_by_identity?']()
      } else {
        return $ret_or_1
      }
    });
    
    $def(self, '$delete', function $Set_delete$11(o) {
      var self = this;

      
      self.hash.$delete(o);
      return self;
    });
    
    $def(self, '$delete?', function $Set_delete$ques$12(o) {
      var self = this;

      if ($truthy(self['$include?'](o))) {
        
        self.$delete(o);
        return self;
      } else {
        return nil
      }
    });
    
    $def(self, '$delete_if', function $$delete_if() {
      var $yield = $$delete_if.$$p || nil, self = this;

      $$delete_if.$$p = null;
      
      if (!($yield !== nil)) {
        return self.$enum_for("delete_if")
      }      $send($send(self, 'select', [], function $$13(o){
        
        if (o == null) o = nil;
        return Opal.yield1($yield, o);}), 'each', [], function $$14(o){var self = $$14.$$s == null ? this : $$14.$$s;
        if (self.hash == null) self.hash = nil;

        
        if (o == null) o = nil;
        return self.hash.$delete(o);}, {$$s: self});
      return self;
    });
    
    $def(self, '$freeze', function $$freeze() {
      var self = this;

      
      if ($truthy(self['$frozen?']())) {
        return self
      }      self.hash.$freeze();
      return $freeze(self);    });
    
    $def(self, '$keep_if', function $$keep_if() {
      var $yield = $$keep_if.$$p || nil, self = this;

      $$keep_if.$$p = null;
      
      if (!($yield !== nil)) {
        return self.$enum_for("keep_if")
      }      $send($send(self, 'reject', [], function $$15(o){
        
        if (o == null) o = nil;
        return Opal.yield1($yield, o);}), 'each', [], function $$16(o){var self = $$16.$$s == null ? this : $$16.$$s;
        if (self.hash == null) self.hash = nil;

        
        if (o == null) o = nil;
        return self.hash.$delete(o);}, {$$s: self});
      return self;
    });
    
    $def(self, '$reject!', function $Set_reject$excl$17() {
      var block = $Set_reject$excl$17.$$p || nil, self = this, before = nil;

      $Set_reject$excl$17.$$p = null;
      if (!(block !== nil)) {
        return self.$enum_for("reject!")
      }      before = self.$size();
      $send(self, 'delete_if', [], block.$to_proc());
      if ($eqeq(self.$size(), before)) {
        return nil
      } else {
        return self
      }    });
    
    $def(self, '$select!', function $Set_select$excl$18() {
      var block = $Set_select$excl$18.$$p || nil, self = this, before = nil;

      $Set_select$excl$18.$$p = null;
      if (!(block !== nil)) {
        return self.$enum_for("select!")
      }      before = self.$size();
      $send(self, 'keep_if', [], block.$to_proc());
      if ($eqeq(self.$size(), before)) {
        return nil
      } else {
        return self
      }    });
    
    $def(self, '$add?', function $Set_add$ques$19(o) {
      var self = this;

      if ($truthy(self['$include?'](o))) {
        return nil
      } else {
        return self.$add(o)
      }
    });
    
    $def(self, '$each', function $$each() {
      var block = $$each.$$p || nil, self = this;

      $$each.$$p = null;
      if (!(block !== nil)) {
        return self.$enum_for("each")
      }      $send(self.hash, 'each_key', [], block.$to_proc());
      return self;
    });
    
    $def(self, '$empty?', function $Set_empty$ques$20() {
      var self = this;

      return self.hash['$empty?']()
    });
    
    $def(self, '$eql?', function $Set_eql$ques$21(other) {
      var self = this;

      return self.hash['$eql?']($send(other, 'instance_eval', [], function $$22(){var self = $$22.$$s == null ? this : $$22.$$s;
        if (self.hash == null) self.hash = nil;

        return self.hash}, {$$s: self}))
    });
    
    $def(self, '$clear', function $$clear() {
      var self = this;

      
      self.hash.$clear();
      return self;
    });
    
    $def(self, '$include?', function $Set_include$ques$23(o) {
      var self = this;

      return self.hash['$include?'](o)
    });
    
    $def(self, '$merge', function $$merge(enum$) {
      var self = this;

      
      $send(enum$, 'each', [], function $$24(item){var self = $$24.$$s == null ? this : $$24.$$s;

        
        if (item == null) item = nil;
        return self.$add(item);}, {$$s: self});
      return self;
    });
    
    $def(self, '$replace', function $$replace(enum$) {
      var self = this;

      
      self.$clear();
      self.$merge(enum$);
      return self;
    });
    
    $def(self, '$size', function $$size() {
      var self = this;

      return self.hash.$size()
    });
    
    $def(self, '$subtract', function $$subtract(enum$) {
      var self = this;

      
      $send(enum$, 'each', [], function $$25(item){var self = $$25.$$s == null ? this : $$25.$$s;

        
        if (item == null) item = nil;
        return self.$delete(item);}, {$$s: self});
      return self;
    });
    
    $def(self, '$|', function $Set_$$26(enum$) {
      var self = this;

      
      if (!$truthy(enum$['$respond_to?']("each"))) {
        $Kernel.$raise($$$('ArgumentError'), "value must be enumerable");
      }      return self.$dup().$merge(enum$);
    });
    
    function is_set(set) {
      ($truthy(($ret_or_1 = (set)['$is_a?']($$$('Set')))) ? ($ret_or_1) : ($Kernel.$raise($$$('ArgumentError'), "value must be a set")));
    }
    
    $def(self, '$superset?', function $Set_superset$ques$27(set) {
      var self = this;

      
      is_set(set);
      if ($truthy($rb_lt(self.$size(), set.$size()))) {
        return false
      }      return $send(set, 'all?', [], function $$28(o){var self = $$28.$$s == null ? this : $$28.$$s;

        
        if (o == null) o = nil;
        return self['$include?'](o);}, {$$s: self});
    });
    
    $def(self, '$proper_superset?', function $Set_proper_superset$ques$29(set) {
      var self = this;

      
      is_set(set);
      if ($truthy($rb_le(self.$size(), set.$size()))) {
        return false
      }      return $send(set, 'all?', [], function $$30(o){var self = $$30.$$s == null ? this : $$30.$$s;

        
        if (o == null) o = nil;
        return self['$include?'](o);}, {$$s: self});
    });
    
    $def(self, '$subset?', function $Set_subset$ques$31(set) {
      var self = this;

      
      is_set(set);
      if ($truthy($rb_lt(set.$size(), self.$size()))) {
        return false
      }      return $send(self, 'all?', [], function $$32(o){
        
        if (o == null) o = nil;
        return set['$include?'](o);});
    });
    
    $def(self, '$proper_subset?', function $Set_proper_subset$ques$33(set) {
      var self = this;

      
      is_set(set);
      if ($truthy($rb_le(set.$size(), self.$size()))) {
        return false
      }      return $send(self, 'all?', [], function $$34(o){
        
        if (o == null) o = nil;
        return set['$include?'](o);});
    });
    
    $def(self, '$intersect?', function $Set_intersect$ques$35(set) {
      var self = this;

      
      is_set(set);
      if ($truthy($rb_lt(self.$size(), set.$size()))) {
        return $send(self, 'any?', [], function $$36(o){
          
          if (o == null) o = nil;
          return set['$include?'](o);})
      } else {
        return $send(set, 'any?', [], function $$37(o){var self = $$37.$$s == null ? this : $$37.$$s;

          
          if (o == null) o = nil;
          return self['$include?'](o);}, {$$s: self})
      }    });
    
    $def(self, '$disjoint?', function $Set_disjoint$ques$38(set) {
      var self = this;

      return self['$intersect?'](set)['$!']()
    });
    
    $def(self, '$to_a', function $$to_a() {
      var self = this;

      return self.hash.$keys()
    });
    $alias(self, "+", "|");
    $alias(self, "<", "proper_subset?");
    $alias(self, "<<", "add");
    $alias(self, "<=", "subset?");
    $alias(self, ">", "proper_superset?");
    $alias(self, ">=", "superset?");
    $alias(self, "difference", "-");
    $alias(self, "filter!", "select!");
    $alias(self, "length", "size");
    $alias(self, "map!", "collect!");
    $alias(self, "member?", "include?");
    return $alias(self, "union", "|");
  })('::', null, $nesting)
};

Opal.modules["corelib/dir"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $def = Opal.def, $truthy = Opal.truthy, $alias = Opal.alias, nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('[],pwd');
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Dir');

    return (function(self, $parent_nesting) {
      
      
      
      $def(self, '$chdir', function $$chdir(dir) {
        var $yield = $$chdir.$$p || nil, prev_cwd = nil;

        $$chdir.$$p = null;
        return (function() { try {
        
        prev_cwd = Opal.current_dir;
        Opal.current_dir = dir;
        return Opal.yieldX($yield, []);;
        } finally {
          Opal.current_dir = prev_cwd;
        } })()
      });
      
      $def(self, '$pwd', function $$pwd() {
        
        return Opal.current_dir || '.';
      });
      
      $def(self, '$home', function $$home() {
        var $ret_or_1 = nil;

        if ($truthy(($ret_or_1 = $$$('ENV')['$[]']("HOME")))) {
          return $ret_or_1
        } else {
          return "."
        }
      });
      return $alias(self, "getwd", "pwd");
    })(Opal.get_singleton_class(self))
  })('::', null)
};

Opal.modules["corelib/file"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $truthy = Opal.truthy, $klass = Opal.klass, $const_set = Opal.const_set, $Opal = Opal.Opal, $regexp = Opal.regexp, $rb_plus = Opal.rb_plus, $def = Opal.def, $Kernel = Opal.Kernel, $eqeq = Opal.eqeq, $rb_lt = Opal.rb_lt, $rb_minus = Opal.rb_minus, $range = Opal.range, $send = Opal.send, $slice = Opal.slice, $alias = Opal.alias, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('respond_to?,to_path,coerce_to!,pwd,split,sub,+,unshift,join,home,raise,start_with?,absolute_path,==,<,dirname,-,basename,empty?,rindex,[],length,nil?,gsub,find,=~,map,each_with_index,flatten,reject,to_proc,end_with?,expand_path,exist?');
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'File');

    var $nesting = [self].concat($parent_nesting), windows_root_rx = nil;

    
    $const_set($nesting[0], 'Separator', $const_set($nesting[0], 'SEPARATOR', "/"));
    $const_set($nesting[0], 'ALT_SEPARATOR', nil);
    $const_set($nesting[0], 'PATH_SEPARATOR', ":");
    $const_set($nesting[0], 'FNM_SYSCASE', 0);
    windows_root_rx = /^[a-zA-Z]:(?:\\|\/)/;
    return (function(self, $parent_nesting) {
      var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting);

      
      
      $def(self, '$absolute_path', function $$absolute_path(path, basedir) {
        var sep = nil, sep_chars = nil, new_parts = nil, $ret_or_1 = nil, path_abs = nil, basedir_abs = nil, parts = nil, leading_sep = nil, abs = nil, new_path = nil;

        
        if (basedir == null) basedir = nil;
        sep = $$('SEPARATOR');
        sep_chars = $sep_chars();
        new_parts = [];
        path = ($truthy(path['$respond_to?']("to_path")) ? (path.$to_path()) : (path));
        path = $Opal['$coerce_to!'](path, $$$('String'), "to_str");
        basedir = ($truthy(($ret_or_1 = basedir)) ? ($ret_or_1) : ($$$('Dir').$pwd()));
        path_abs = path.substr(0, sep.length) === sep || windows_root_rx.test(path);
        basedir_abs = basedir.substr(0, sep.length) === sep || windows_root_rx.test(basedir);
        if ($truthy(path_abs)) {
          
          parts = path.$split($regexp(["[", sep_chars, "]"]));
          leading_sep = windows_root_rx.test(path) ? '' : path.$sub($regexp(["^([", sep_chars, "]+).*$"]), "\\1");
          abs = true;
        } else {
          
          parts = $rb_plus(basedir.$split($regexp(["[", sep_chars, "]"])), path.$split($regexp(["[", sep_chars, "]"])));
          leading_sep = windows_root_rx.test(basedir) ? '' : basedir.$sub($regexp(["^([", sep_chars, "]+).*$"]), "\\1");
          abs = basedir_abs;
        }        
        var part;
        for (var i = 0, ii = parts.length; i < ii; i++) {
          part = parts[i];

          if (
            (part === nil) ||
            (part === ''  && ((new_parts.length === 0) || abs)) ||
            (part === '.' && ((new_parts.length === 0) || abs))
          ) {
            continue;
          }
          if (part === '..') {
            new_parts.pop();
          } else {
            new_parts.push(part);
          }
        }

        if (!abs && parts[0] !== '.') {
          new_parts.$unshift(".");
        }
        new_path = new_parts.$join(sep);
        if ($truthy(abs)) {
          new_path = $rb_plus(leading_sep, new_path);
        }        return new_path;
      }, -2);
      
      $def(self, '$expand_path', function $$expand_path(path, basedir) {
        var self = this, sep = nil, sep_chars = nil, home = nil, leading_sep = nil, home_path_regexp = nil;

        
        if (basedir == null) basedir = nil;
        sep = $$('SEPARATOR');
        sep_chars = $sep_chars();
        if ($truthy(path[0] === '~' || (basedir && basedir[0] === '~'))) {
          
          home = $$('Dir').$home();
          if (!$truthy(home)) {
            $Kernel.$raise($$$('ArgumentError'), "couldn't find HOME environment -- expanding `~'");
          }          leading_sep = windows_root_rx.test(home) ? '' : home.$sub($regexp(["^([", sep_chars, "]+).*$"]), "\\1");
          if (!$truthy(home['$start_with?'](leading_sep))) {
            $Kernel.$raise($$$('ArgumentError'), "non-absolute home");
          }          home = $rb_plus(home, sep);
          home_path_regexp = $regexp(["^\\~(?:", sep, "|$)"]);
          path = path.$sub(home_path_regexp, home);
          if ($truthy(basedir)) {
            basedir = basedir.$sub(home_path_regexp, home);
          }        }        return self.$absolute_path(path, basedir);
      }, -2);
      
      // Coerce a given path to a path string using #to_path and #to_str
      function $coerce_to_path(path) {
        if ($truthy((path)['$respond_to?']("to_path"))) {
          path = path.$to_path();
        }

        path = $Opal['$coerce_to!'](path, $$$('String'), "to_str");

        return path;
      }

      // Return a RegExp compatible char class
      function $sep_chars() {
        if ($$('ALT_SEPARATOR') === nil) {
          return Opal.escape_regexp($$('SEPARATOR'));
        } else {
          return Opal.escape_regexp($rb_plus($$('SEPARATOR'), $$('ALT_SEPARATOR')));
        }
      }
      
      $def(self, '$dirname', function $$dirname(path, level) {
        var self = this, sep_chars = nil;

        
        if (level == null) level = 1;
        if ($eqeq(level, 0)) {
          return path
        }        if ($truthy($rb_lt(level, 0))) {
          $Kernel.$raise($$$('ArgumentError'), "level can't be negative");
        }        sep_chars = $sep_chars();
        path = $coerce_to_path(path);
        
        var absolute = path.match(new RegExp("^[" + (sep_chars) + "]")), out;

        path = path.replace(new RegExp("[" + (sep_chars) + "]+$"), ''); // remove trailing separators
        path = path.replace(new RegExp("[^" + (sep_chars) + "]+$"), ''); // remove trailing basename
        path = path.replace(new RegExp("[" + (sep_chars) + "]+$"), ''); // remove final trailing separators

        if (path === '') {
          out = absolute ? '/' : '.';
        }
        else {
          out = path;
        }

        if (level == 1) {
          return out;
        }
        else {
          return self.$dirname(out, $rb_minus(level, 1))
        }
      }, -2);
      
      $def(self, '$basename', function $$basename(name, suffix) {
        var sep_chars = nil;

        
        if (suffix == null) suffix = nil;
        sep_chars = $sep_chars();
        name = $coerce_to_path(name);
        
        if (name.length == 0) {
          return name;
        }

        if (suffix !== nil) {
          suffix = $Opal['$coerce_to!'](suffix, $$$('String'), "to_str");
        } else {
          suffix = null;
        }

        name = name.replace(new RegExp("(.)[" + (sep_chars) + "]*$"), '$1');
        name = name.replace(new RegExp("^(?:.*[" + (sep_chars) + "])?([^" + (sep_chars) + "]+)$"), '$1');

        if (suffix === ".*") {
          name = name.replace(/\.[^\.]+$/, '');
        } else if(suffix !== null) {
          suffix = Opal.escape_regexp(suffix);
          name = name.replace(new RegExp("" + (suffix) + "$"), '');
        }

        return name;
      }, -2);
      
      $def(self, '$extname', function $$extname(path) {
        var self = this, filename = nil, last_dot_idx = nil;

        
        path = $coerce_to_path(path);
        filename = self.$basename(path);
        if ($truthy(filename['$empty?']())) {
          return ""
        }        last_dot_idx = filename['$[]']($range(1, -1, false)).$rindex(".");
        if (($truthy(last_dot_idx['$nil?']()) || ($eqeq($rb_plus(last_dot_idx, 1), $rb_minus(filename.$length(), 1))))) {
          return ""
        } else {
          return filename['$[]'](Opal.Range.$new($rb_plus(last_dot_idx, 1), -1, false))
        }      });
      
      $def(self, '$exist?', function $exist$ques$1(path) {
        
        return Opal.modules[path] != null
      });
      
      $def(self, '$directory?', function $directory$ques$2(path) {
        var files = nil;

        
        files = [];
        
        for (var key in Opal.modules) {
          files.push(key);
        }
        path = path.$gsub($regexp(["(^.", $$('SEPARATOR'), "+|", $$('SEPARATOR'), "+$)"]));
        return $send(files, 'find', [], function $$3(f){
          
          if (f == null) f = nil;
          return f['$=~']($regexp(["^", path]));});
      });
      
      $def(self, '$join', function $$join($a) {
        var $post_args, paths, result = nil;

        
        $post_args = $slice(arguments);
        paths = $post_args;
        if ($truthy(paths['$empty?']())) {
          return ""
        }        result = "";
        paths = $send(paths.$flatten().$each_with_index(), 'map', [], function $$4(item, index){
          
          if (item == null) item = nil;
          if (index == null) index = nil;
          if (($eqeq(index, 0) && ($truthy(item['$empty?']())))) {
            return $$('SEPARATOR')
          } else if (($eqeq(paths.$length(), $rb_plus(index, 1)) && ($truthy(item['$empty?']())))) {
            return $$('SEPARATOR')
          } else {
            return item
          }});
        paths = $send(paths, 'reject', [], "empty?".$to_proc());
        $send(paths, 'each_with_index', [], function $$5(item, index){var next_item = nil;

          
          if (item == null) item = nil;
          if (index == null) index = nil;
          next_item = paths['$[]']($rb_plus(index, 1));
          if ($truthy(next_item['$nil?']())) {
            return (result = "" + (result) + (item))
          } else {
            
            if (($truthy(item['$end_with?']($$('SEPARATOR'))) && ($truthy(next_item['$start_with?']($$('SEPARATOR')))))) {
              item = item.$sub($regexp([$$('SEPARATOR'), "+$"]), "");
            }            return (result = (($truthy(item['$end_with?']($$('SEPARATOR'))) || ($truthy(next_item['$start_with?']($$('SEPARATOR'))))) ? ("" + (result) + (item)) : ("" + (result) + (item) + ($$('SEPARATOR')))));
          }});
        return result;
      }, -1);
      
      $def(self, '$split', function $$split(path) {
        
        return path.$split($$('SEPARATOR'))
      });
      $alias(self, "realpath", "expand_path");
      return $alias(self, "exists?", "exist?");
    })(Opal.get_singleton_class(self), $nesting);
  })('::', $$$('IO'), $nesting)
};

Opal.modules["corelib/process/base"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $slice = Opal.slice, $defs = Opal.defs, $return_val = Opal.return_val, nil = Opal.nil;

  
  (function($base, $super) {
    var self = $klass($base, $super, 'Signal');

    
    return $defs(self, '$trap', function $$trap($a) {

      
      $slice(arguments);
      return nil;
    }, -1)
  })('::', null);
  return (function($base, $super) {
    var self = $klass($base, $super, 'GC');

    
    return $defs(self, '$start', $return_val(nil))
  })('::', null);
};

Opal.modules["corelib/process"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $module = Opal.module, $defs = Opal.defs, $truthy = Opal.truthy, $return_val = Opal.return_val, $Kernel = Opal.Kernel, nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('const_set,size,<<,__register_clock__,to_f,now,new,[],raise');
  return (function($base) {
    var self = $module($base, 'Process');

    var monotonic = nil;

    
    self.__clocks__ = [];
    $defs(self, '$__register_clock__', function $$__register_clock__(name, func) {
      var self = this;
      if (self.__clocks__ == null) self.__clocks__ = nil;

      
      self.$const_set(name, self.__clocks__.$size());
      return self.__clocks__['$<<'](func);
    });
    self.$__register_clock__("CLOCK_REALTIME", function() { return Date.now() });
    monotonic = false;
    
    if (Opal.global.performance) {
      monotonic = function() {
        return performance.now()
      };
    }
    else if (Opal.global.process && process.hrtime) {
      // let now be the base to get smaller numbers
      var hrtime_base = process.hrtime();

      monotonic = function() {
        var hrtime = process.hrtime(hrtime_base);
        var us = (hrtime[1] / 1000) | 0; // cut below microsecs;
        return ((hrtime[0] * 1000) + (us / 1000));
      };
    }
    if ($truthy(monotonic)) {
      self.$__register_clock__("CLOCK_MONOTONIC", monotonic);
    }    $defs(self, '$pid', $return_val(0));
    $defs(self, '$times', function $$times() {
      var t = nil;

      
      t = $$$('Time').$now().$to_f();
      return $$$($$$('Benchmark'), 'Tms').$new(t, t, t, t, t);
    });
    return $defs(self, '$clock_gettime', function $$clock_gettime(clock_id, unit) {
      var self = this, clock = nil;
      if (self.__clocks__ == null) self.__clocks__ = nil;

      
      if (unit == null) unit = "float_second";
      if ($truthy(((clock = self.__clocks__['$[]'](clock_id))))) ; else {
        $Kernel.$raise($$$($$$('Errno'), 'EINVAL'), "clock_gettime(" + (clock_id) + ") " + (self.__clocks__['$[]'](clock_id)));
      }      
      var ms = clock();
      switch (unit) {
        case 'float_second':      return  (ms / 1000);         // number of seconds as a float (default)
        case 'float_millisecond': return  (ms / 1);            // number of milliseconds as a float
        case 'float_microsecond': return  (ms * 1000);         // number of microseconds as a float
        case 'second':            return ((ms / 1000)    | 0); // number of seconds as an integer
        case 'millisecond':       return ((ms / 1)       | 0); // number of milliseconds as an integer
        case 'microsecond':       return ((ms * 1000)    | 0); // number of microseconds as an integer
        case 'nanosecond':        return ((ms * 1000000) | 0); // number of nanoseconds as an integer
        default: $Kernel.$raise($$$('ArgumentError'), "unexpected unit: " + (unit));
      }
    }, -2);
  })('::')
};

Opal.modules["corelib/random/formatter"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $module = Opal.module, $def = Opal.def, $range = Opal.range, $send = Opal.send, $rb_divide = Opal.rb_divide, $Kernel = Opal.Kernel, $Opal = Opal.Opal, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('_verify_count,bytes,encode,strict_encode64,random_bytes,urlsafe_encode64,split,hex,[]=,[],map,to_proc,join,times,<<,|,ord,/,abs,random_float,raise,coerce_to!,flatten,new,random_number,length,include,extend');
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Random');

    var $nesting = [self].concat($parent_nesting);

    
    (function($base, $parent_nesting) {
      var self = $module($base, 'Formatter');

      var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting);

      
      
      $def(self, '$hex', function $$hex(count) {
        var self = this;

        
        if (count == null) count = nil;
        count = $$$('Random').$_verify_count(count);
        
        var bytes = self.$bytes(count);
        var out = "";
        for (var i = 0; i < count; i++) {
          out += bytes.charCodeAt(i).toString(16).padStart(2, '0');
        }
        return (out).$encode("US-ASCII");
      }, -1);
      
      $def(self, '$random_bytes', function $$random_bytes(count) {
        var self = this;

        
        if (count == null) count = nil;
        return self.$bytes(count);
      }, -1);
      
      $def(self, '$base64', function $$base64(count) {
        var self = this;

        
        if (count == null) count = nil;
        return $$$('Base64').$strict_encode64(self.$random_bytes(count)).$encode("US-ASCII");
      }, -1);
      
      $def(self, '$urlsafe_base64', function $$urlsafe_base64(count, padding) {
        var self = this;

        
        if (count == null) count = nil;
        if (padding == null) padding = false;
        return $$$('Base64').$urlsafe_encode64(self.$random_bytes(count), padding).$encode("US-ASCII");
      }, -1);
      
      $def(self, '$uuid', function $$uuid() {
        var self = this, str = nil;

        
        str = self.$hex(16).$split("");
        str['$[]='](12, "4");
        str['$[]='](16, (parseInt(str['$[]'](16), 16) & 3 | 8).toString(16));
        str = [str['$[]']($range(0, 8, true)), str['$[]']($range(8, 12, true)), str['$[]']($range(12, 16, true)), str['$[]']($range(16, 20, true)), str['$[]']($range(20, 32, true))];
        str = $send(str, 'map', [], "join".$to_proc());
        return str.$join("-");
      });
      
      $def(self, '$random_float', function $$random_float() {
        var self = this, bs = nil, num = nil;

        
        bs = self.$bytes(4);
        num = 0;
        $send((4), 'times', [], function $$1(i){
          
          if (i == null) i = nil;
          num = num['$<<'](8);
          return (num = num['$|'](bs['$[]'](i).$ord()));});
        return $rb_divide(num.$abs(), 2147483647);
      });
      
      $def(self, '$random_number', function $$random_number(limit) {
        var self = this;
        
        function randomFloat() {
          return self.$random_float();
        }

        function randomInt(max) {
          return Math.floor(randomFloat() * max);
        }

        function randomRange() {
          var min = limit.begin,
              max = limit.end;

          if (min === nil || max === nil) {
            return nil;
          }

          var length = max - min;

          if (length < 0) {
            return nil;
          }

          if (length === 0) {
            return min;
          }

          if (max % 1 === 0 && min % 1 === 0 && !limit.excl) {
            length++;
          }

          return randomInt(length) + min;
        }

        if (limit == null) {
          return randomFloat();
        } else if (limit.$$is_range) {
          return randomRange();
        } else if (limit.$$is_number) {
          if (limit <= 0) {
            $Kernel.$raise($$$('ArgumentError'), "invalid argument - " + (limit));
          }

          if (limit % 1 === 0) {
            // integer
            return randomInt(limit);
          } else {
            return randomFloat() * limit;
          }
        } else {
          limit = $Opal['$coerce_to!'](limit, $$$('Integer'), "to_int");

          if (limit <= 0) {
            $Kernel.$raise($$$('ArgumentError'), "invalid argument - " + (limit));
          }

          return randomInt(limit);
        }
      }, -1);
      return $def(self, '$alphanumeric', function $$alphanumeric(count) {
        var self = this, map = nil;

        
        if (count == null) count = nil;
        count = $$('Random').$_verify_count(count);
        map = $send([$range("0", "9", false), $range("a", "z", false), $range("A", "Z", false)], 'map', [], "to_a".$to_proc()).$flatten();
        return $send($$$('Array'), 'new', [count], function $$2(i){var self = $$2.$$s == null ? this : $$2.$$s;
          return map['$[]'](self.$random_number(map.$length()));}, {$$s: self}).$join();
      }, -1);
    })(self, $nesting);
    self.$include($$$($$$('Random'), 'Formatter'));
    return self.$extend($$$($$$('Random'), 'Formatter'));
  })('::', null, $nesting)
};

Opal.modules["corelib/random/mersenne_twister"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $const_set = Opal.const_set, $send = Opal.send, nil = Opal.nil, $$$ = Opal.$$$, mersenne_twister = nil;

  Opal.add_stubs('generator=');
  
  mersenne_twister = (function() {
  /* Period parameters */
  var N = 624;
  var M = 397;
  var MATRIX_A = 0x9908b0df;      /* constant vector a */
  var UMASK = 0x80000000;         /* most significant w-r bits */
  var LMASK = 0x7fffffff;         /* least significant r bits */
  var MIXBITS = function(u,v) { return ( ((u) & UMASK) | ((v) & LMASK) ); };
  var TWIST = function(u,v) { return (MIXBITS((u),(v)) >>> 1) ^ ((v & 0x1) ? MATRIX_A : 0x0); };

  function init(s) {
    var mt = {left: 0, next: N, state: new Array(N)};
    init_genrand(mt, s);
    return mt;
  }

  /* initializes mt[N] with a seed */
  function init_genrand(mt, s) {
    var j;
    mt.state[0] = s >>> 0;
    for (j=1; j<N; j++) {
      mt.state[j] = (1812433253 * ((mt.state[j-1] ^ (mt.state[j-1] >> 30) >>> 0)) + j);
      /* See Knuth TAOCP Vol2. 3rd Ed. P.106 for multiplier. */
      /* In the previous versions, MSBs of the seed affect   */
      /* only MSBs of the array state[].                     */
      /* 2002/01/09 modified by Makoto Matsumoto             */
      mt.state[j] &= 0xffffffff;  /* for >32 bit machines */
    }
    mt.left = 1;
    mt.next = N;
  }

  /* generate N words at one time */
  function next_state(mt) {
    var p = 0, _p = mt.state;
    var j;

    mt.left = N;
    mt.next = 0;

    for (j=N-M+1; --j; p++)
      _p[p] = _p[p+(M)] ^ TWIST(_p[p+(0)], _p[p+(1)]);

    for (j=M; --j; p++)
      _p[p] = _p[p+(M-N)] ^ TWIST(_p[p+(0)], _p[p+(1)]);

    _p[p] = _p[p+(M-N)] ^ TWIST(_p[p+(0)], _p[0]);
  }

  /* generates a random number on [0,0xffffffff]-interval */
  function genrand_int32(mt) {
    /* mt must be initialized */
    var y;

    if (--mt.left <= 0) next_state(mt);
    y = mt.state[mt.next++];

    /* Tempering */
    y ^= (y >>> 11);
    y ^= (y << 7) & 0x9d2c5680;
    y ^= (y << 15) & 0xefc60000;
    y ^= (y >>> 18);

    return y >>> 0;
  }

  function int_pair_to_real_exclusive(a, b) {
    a >>>= 5;
    b >>>= 6;
    return (a*67108864.0+b)*(1.0/9007199254740992.0);
  }

  // generates a random number on [0,1) with 53-bit resolution
  function genrand_real(mt) {
    /* mt must be initialized */
    var a = genrand_int32(mt), b = genrand_int32(mt);
    return int_pair_to_real_exclusive(a, b);
  }

  return { genrand_real: genrand_real, init: init };
})();
  return (function($base, $super) {
    var self = $klass($base, $super, 'Random');

    var $a;

    
    var MAX_INT = Number.MAX_SAFE_INTEGER || Math.pow(2, 53) - 1;
    $const_set(self, 'MERSENNE_TWISTER_GENERATOR', {
    new_seed: function() { return Math.round(Math.random() * MAX_INT); },
    reseed: function(seed) { return mersenne_twister.init(seed); },
    rand: function(mt) { return mersenne_twister.genrand_real(mt); }
  });
    return ($a = [$$$(self, 'MERSENNE_TWISTER_GENERATOR')], $send(self, 'generator=', $a), $a[$a.length - 1]);
  })('::', null);
};

Opal.modules["corelib/random"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $truthy = Opal.truthy, $klass = Opal.klass, $Kernel = Opal.Kernel, $defs = Opal.defs, $Opal = Opal.Opal, $def = Opal.def, $eqeqeq = Opal.eqeqeq, $send = Opal.send, self = Opal.top, nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('require,attr_reader,to_int,raise,new_seed,coerce_to!,reseed,rand,seed,bytes,===,==,state,_verify_count,encode,join,new,chr,random_number,random_float,const_defined?,const_set');
  
  self.$require("corelib/random/formatter");
  (function($base, $super) {
    var self = $klass($base, $super, 'Random');

    
    
    self.$attr_reader("seed", "state");
    $defs(self, '$_verify_count', function $$_verify_count(count) {
      
      
      if (!$truthy(count)) count = 16;
      if (typeof count !== "number") count = (count).$to_int();
      if (count < 0) $Kernel.$raise($$$('ArgumentError'), "negative string size (or size too big)");
      count = Math.floor(count);
      return count;
    
    });
    
    $def(self, '$initialize', function $$initialize(seed) {
      var self = this;

      
      if (seed == null) seed = $$$('Random').$new_seed();
      seed = $Opal['$coerce_to!'](seed, $$$('Integer'), "to_int");
      self.state = seed;
      return self.$reseed(seed);
    }, -1);
    
    $def(self, '$reseed', function $$reseed(seed) {
      var self = this;

      
      self.seed = seed;
      return self.$rng = Opal.$$rand.reseed(seed);    });
    $defs(self, '$new_seed', function $$new_seed() {
      
      return Opal.$$rand.new_seed();
    });
    $defs(self, '$rand', function $$rand(limit) {
      var self = this;
      return $$$(self, 'DEFAULT').$rand(limit);
    }, -1);
    $defs(self, '$srand', function $$srand(n) {
      var self = this, previous_seed = nil;

      
      if (n == null) n = $$$('Random').$new_seed();
      n = $Opal['$coerce_to!'](n, $$$('Integer'), "to_int");
      previous_seed = $$$(self, 'DEFAULT').$seed();
      $$$(self, 'DEFAULT').$reseed(n);
      return previous_seed;
    }, -1);
    $defs(self, '$urandom', function $$urandom(size) {
      
      return $$$('SecureRandom').$bytes(size)
    });
    
    $def(self, '$==', function $Random_$eq_eq$1(other) {
      var self = this, $ret_or_1 = nil;

      
      if (!$eqeqeq($$$('Random'), other)) {
        return false
      }      if ($truthy(($ret_or_1 = self.$seed()['$=='](other.$seed())))) {
        return self.$state()['$=='](other.$state())
      } else {
        return $ret_or_1
      }    });
    
    $def(self, '$bytes', function $$bytes(length) {
      var self = this;

      
      length = $$$('Random').$_verify_count(length);
      return $send($$$('Array'), 'new', [length], function $$2(){var self = $$2.$$s == null ? this : $$2.$$s;

        return self.$rand(255).$chr()}, {$$s: self}).$join().$encode("ASCII-8BIT");
    });
    $defs(self, '$bytes', function $$bytes(length) {
      var self = this;

      return $$$(self, 'DEFAULT').$bytes(length)
    });
    
    $def(self, '$rand', function $$rand(limit) {
      var self = this;
      return self.$random_number(limit);
    }, -1);
    
    $def(self, '$random_float', function $$random_float() {
      var self = this;

      
      self.state++;
      return Opal.$$rand.rand(self.$rng);
    
    });
    $defs(self, '$random_float', function $$random_float() {
      var self = this;

      return $$$(self, 'DEFAULT').$random_float()
    });
    return $defs(self, '$generator=', function $Random_generator$eq$3(generator) {
      var self = this;

      
      Opal.$$rand = generator;
      if ($truthy(self['$const_defined?']("DEFAULT"))) {
        return $$$(self, 'DEFAULT').$reseed()
      } else {
        return self.$const_set("DEFAULT", self.$new(self.$new_seed()))
      }    });
  })('::', null);
  return self.$require("corelib/random/mersenne_twister");
};

Opal.modules["corelib/unsupported"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $Kernel = Opal.Kernel, $klass = Opal.klass, $send = Opal.send, $slice = Opal.slice, $module = Opal.module, $def = Opal.def, $return_val = Opal.return_val, $alias = Opal.alias, $defs = Opal.defs, self = Opal.top, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('raise,warn,each,define_method,%,public,private_method_defined?,private_class_method,instance_method,instance_methods,method_defined?,private_methods');
  
  
  var warnings = {};

  function handle_unsupported_feature(message) {
    switch (Opal.config.unsupported_features_severity) {
    case 'error':
      $Kernel.$raise($$$('NotImplementedError'), message);
      break;
    case 'warning':
      warn(message);
      break;
      // noop
    }
  }

  function warn(string) {
    if (warnings[string]) {
      return;
    }

    warnings[string] = true;
    self.$warn(string);
  }
  (function($base, $super) {
    var self = $klass($base, $super, 'String');

    
    
    var ERROR = "String#%s not supported. Mutable String methods are not supported in Opal.";
    return $send(["<<", "capitalize!", "chomp!", "chop!", "downcase!", "gsub!", "lstrip!", "next!", "reverse!", "slice!", "squeeze!", "strip!", "sub!", "succ!", "swapcase!", "tr!", "tr_s!", "upcase!", "prepend", "[]=", "clear", "encode!", "unicode_normalize!"], 'each', [], function $String$1(method_name){var self = $String$1.$$s == null ? this : $String$1.$$s;

      
      if (method_name == null) method_name = nil;
      return $send(self, 'define_method', [method_name], function $$2($a){
        
        $slice(arguments);
        return $Kernel.$raise($$$('NotImplementedError'), (ERROR)['$%'](method_name));}, -1);}, {$$s: self});
  })('::', null);
  (function($base) {
    var self = $module($base, 'Kernel');

    
    
    var ERROR = "Object tainting is not supported by Opal";
    
    $def(self, '$taint', function $$taint() {
      var self = this;

      
      handle_unsupported_feature(ERROR);
      return self;
    });
    
    $def(self, '$untaint', function $$untaint() {
      var self = this;

      
      handle_unsupported_feature(ERROR);
      return self;
    });
    return $def(self, '$tainted?', function $Kernel_tainted$ques$3() {
      
      
      handle_unsupported_feature(ERROR);
      return false;
    });
  })('::');
  (function($base, $super) {
    var self = $klass($base, $super, 'Module');

    
    
    
    $def(self, '$public', function $Module_public$4($a) {
      var $post_args, methods, self = this;

      
      $post_args = $slice(arguments);
      methods = $post_args;
      
      if (methods.length === 0) {
        self.$$module_function = false;
        return nil;
      }
      return (methods.length === 1) ? methods[0] : methods;
    }, -1);
    
    $def(self, '$private_class_method', function $$private_class_method($a) {
      var $post_args, methods;

      
      $post_args = $slice(arguments);
      methods = $post_args;
      return (methods.length === 1) ? methods[0] : methods;    }, -1);
    
    $def(self, '$private_method_defined?', $return_val(false));
    
    $def(self, '$private_constant', function $$private_constant($a) {

      
      $slice(arguments);
      return nil;
    }, -1);
    $alias(self, "nesting", "public");
    $alias(self, "private", "public");
    $alias(self, "protected", "public");
    $alias(self, "protected_method_defined?", "private_method_defined?");
    $alias(self, "public_class_method", "private_class_method");
    $alias(self, "public_instance_method", "instance_method");
    $alias(self, "public_instance_methods", "instance_methods");
    return $alias(self, "public_method_defined?", "method_defined?");
  })('::', null);
  (function($base) {
    var self = $module($base, 'Kernel');

    
    
    
    $def(self, '$private_methods', function $$private_methods($a) {

      
      $slice(arguments);
      return [];
    }, -1);
    $alias(self, "protected_methods", "private_methods");
    $alias(self, "private_instance_methods", "private_methods");
    return $alias(self, "protected_instance_methods", "private_methods");
  })('::');
  (function($base, $parent_nesting) {
    var self = $module($base, 'Kernel');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting);

    return $def(self, '$eval', function $Kernel_eval$5($a) {

      
      $slice(arguments);
      return $Kernel.$raise($$$('NotImplementedError'), "To use Kernel#eval, you must first require 'opal-parser'. " + ("See https://github.com/opal/opal/blob/" + ($$('RUBY_ENGINE_VERSION')) + "/docs/opal_parser.md for details."));
    }, -1)
  })('::', $nesting);
  $defs(self, '$public', function $public$6($a) {
    var $post_args, methods;

    
    $post_args = $slice(arguments);
    methods = $post_args;
    return (methods.length === 1) ? methods[0] : methods;  }, -1);
  return $defs(self, '$private', function $private$7($a) {
    var $post_args, methods;

    
    $post_args = $slice(arguments);
    methods = $post_args;
    return (methods.length === 1) ? methods[0] : methods;  }, -1);
};

Opal.queue(function(Opal) {/* Generated by Opal 1.7.3 */
  var $Object = Opal.Object; Opal.nil;

  Opal.add_stubs('require,autoload');
  
  $Object.$require("opal/base");
  $Object.$require("opal/mini");
  $Object.$require("corelib/kernel/format");
  $Object.$require("corelib/string/encoding");
  $Object.$autoload("Math", "corelib/math");
  $Object.$require("corelib/complex/base");
  $Object.$autoload("Complex", "corelib/complex");
  $Object.$require("corelib/rational/base");
  $Object.$autoload("Rational", "corelib/rational");
  $Object.$require("corelib/time");
  $Object.$autoload("Struct", "corelib/struct");
  $Object.$autoload("Set", "corelib/set");
  $Object.$autoload("Dir", "corelib/dir");
  $Object.$autoload("File", "corelib/file");
  $Object.$require("corelib/process/base");
  $Object.$autoload("Process", "corelib/process");
  $Object.$autoload("Random", "corelib/random");
  return $Object.$require("corelib/unsupported");
});


var Opal$1 = Opal;

Opal.modules["corelib/comparable"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $truthy = Opal.truthy, $module = Opal.module, $rb_gt = Opal.rb_gt, $rb_lt = Opal.rb_lt, $eqeqeq = Opal.eqeqeq, $Kernel = Opal.Kernel, $def = Opal.def, nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('>,<,===,raise,class,<=>,equal?');
  return (function($base) {
    var self = $module($base, 'Comparable');

    var $ret_or_1 = nil;

    
    
    function normalize(what) {
      if (Opal.is_a(what, Opal.Integer)) { return what; }

      if ($rb_gt(what, 0)) { return 1; }
      if ($rb_lt(what, 0)) { return -1; }
      return 0;
    }

    function fail_comparison(lhs, rhs) {
      var class_name;
      (($eqeqeq(nil, ($ret_or_1 = rhs)) || (($eqeqeq(true, $ret_or_1) || (($eqeqeq(false, $ret_or_1) || (($eqeqeq($$$('Integer'), $ret_or_1) || ($eqeqeq($$$('Float'), $ret_or_1))))))))) ? (class_name = rhs.$inspect()) : (class_name = rhs.$$class));
      $Kernel.$raise($$$('ArgumentError'), "comparison of " + ((lhs).$class()) + " with " + (class_name) + " failed");
    }

    function cmp_or_fail(lhs, rhs) {
      var cmp = (lhs)['$<=>'](rhs);
      if (!$truthy(cmp)) fail_comparison(lhs, rhs);
      return normalize(cmp);
    }
    
    $def(self, '$==', function $Comparable_$eq_eq$1(other) {
      var self = this, cmp = nil;

      
      if ($truthy(self['$equal?'](other))) {
        return true
      }      
      if (self["$<=>"] == Opal.Kernel["$<=>"]) {
        return false;
      }

      // check for infinite recursion
      if (self.$$comparable) {
        self.$$comparable = false;
        return false;
      }
      if (!$truthy((cmp = self['$<=>'](other)))) {
        return false
      }      return normalize(cmp) == 0;    });
    
    $def(self, '$>', function $Comparable_$gt$2(other) {
      var self = this;

      return cmp_or_fail(self, other) > 0;
    });
    
    $def(self, '$>=', function $Comparable_$gt_eq$3(other) {
      var self = this;

      return cmp_or_fail(self, other) >= 0;
    });
    
    $def(self, '$<', function $Comparable_$lt$4(other) {
      var self = this;

      return cmp_or_fail(self, other) < 0;
    });
    
    $def(self, '$<=', function $Comparable_$lt_eq$5(other) {
      var self = this;

      return cmp_or_fail(self, other) <= 0;
    });
    
    $def(self, '$between?', function $Comparable_between$ques$6(min, max) {
      var self = this;

      
      if ($rb_lt(self, min)) {
        return false
      }      if ($rb_gt(self, max)) {
        return false
      }      return true;
    });
    return $def(self, '$clamp', function $$clamp(min, max) {
      var self = this;

      
      if (max == null) max = nil;
      
      var c, excl;

      if (max === nil) {
        // We are dealing with a new Ruby 2.7 behaviour that we are able to
        // provide a single Range argument instead of 2 Comparables.

        if (!Opal.is_a(min, Opal.Range)) {
          $Kernel.$raise($$$('TypeError'), "wrong argument type " + (min.$class()) + " (expected Range)");
        }

        excl = min.excl;
        max = min.end;
        min = min.begin;

        if (max !== nil && excl) {
          $Kernel.$raise($$$('ArgumentError'), "cannot clamp with an exclusive range");
        }
      }

      if (min !== nil && max !== nil && cmp_or_fail(min, max) > 0) {
        $Kernel.$raise($$$('ArgumentError'), "min argument must be smaller than max argument");
      }

      if (min !== nil) {
        c = cmp_or_fail(self, min);

        if (c == 0) return self;
        if (c < 0) return min;
      }

      if (max !== nil) {
        c = cmp_or_fail(self, max);

        if (c > 0) return max;
      }

      return self;
    }, -2);
  })('::')
};

Opal.modules["pathname"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $const_set = Opal.const_set, $regexp = Opal.regexp, $eqeqeq = Opal.eqeqeq, $truthy = Opal.truthy, $eqeq = Opal.eqeq, $def = Opal.def, $defs = Opal.defs, $to_ary = Opal.to_ary, $slice = Opal.slice, $send = Opal.send, $to_a = Opal.to_a, $return_ivar = Opal.return_ivar, $neqeq = Opal.neqeq, $rb_plus = Opal.rb_plus, $not = Opal.not, $thrower = Opal.thrower, $alias = Opal.alias, $module = Opal.module, self = Opal.top, $nesting = [], nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('require,include,quote,===,to_s,path,respond_to?,to_path,is_a?,nil?,raise,class,==,new,pwd,attr_reader,!,relative?,chop_basename,basename,=~,source,[],rindex,sub,absolute?,expand_path,plus,unshift,length,!=,empty?,first,shift,+,join,dirname,pop,reverse_each,directory?,extname,<=>,nonzero?,proc,casecmp,cleanpath,inspect,include?,fill,map,entries');
  
  self.$require("corelib/comparable");
  (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'Pathname');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting), $proto = self.$$prototype;

    $proto.path = nil;
    
    self.$include($$('Comparable'));
    $const_set($nesting[0], 'SEPARATOR_PAT', $regexp([$$('Regexp').$quote($$$($$('File'), 'SEPARATOR'))]));
    
    $def(self, '$initialize', function $$initialize(path) {
      var self = this;

      
      if ($eqeqeq($$('Pathname'), path)) {
        self.path = path.$path().$to_s();
      } else if ($truthy(path['$respond_to?']("to_path"))) {
        self.path = path.$to_path();
      } else if ($truthy(path['$is_a?']($$('String')))) {
        self.path = path;
      } else if ($truthy(path['$nil?']())) {
        self.$raise($$('TypeError'), "no implicit conversion of nil into String");
      } else {
        self.$raise($$('TypeError'), "no implicit conversion of " + (path.$class()) + " into String");
      }      if ($eqeq(self.path, "\u0000")) {
        return self.$raise($$('ArgumentError'))
      } else {
        return nil
      }    });
    $defs(self, '$pwd', function $$pwd() {
      var self = this;

      return self.$new($$('Dir').$pwd())
    });
    self.$attr_reader("path");
    
    $def(self, '$==', function $Pathname_$eq_eq$1(other) {
      var self = this;

      return other.$path()['$=='](self.path)
    });
    
    $def(self, '$absolute?', function $Pathname_absolute$ques$2() {
      var self = this;

      return self['$relative?']()['$!']()
    });
    
    $def(self, '$relative?', function $Pathname_relative$ques$3() {
      var $a, $b, self = this, path = nil, r = nil;

      
      path = self.path;
      while ($truthy((r = self.$chop_basename(path)))) {
      $b = r, $a = $to_ary($b), (path = ($a[0] == null ? nil : $a[0]));
      }      return path['$==']("");
    });
    
    $def(self, '$chop_basename', function $$chop_basename(path) {
      var base = nil;

      
      base = $$('File').$basename(path);
      if ($truthy($$('Regexp').$new("^" + ($$$($$('Pathname'), 'SEPARATOR_PAT').$source()) + "?$")['$=~'](base))) {
        return nil
      } else {
        return [path['$[]'](0, path.$rindex(base)), base]
      }    });
    
    $def(self, '$root?', function $Pathname_root$ques$4() {
      var self = this;

      return self.path['$==']("/")
    });
    
    $def(self, '$parent', function $$parent() {
      var self = this, new_path = nil;

      
      new_path = self.path.$sub(/\/([^\/]+\/?$)/, "");
      if ($eqeq(new_path, "")) {
        new_path = ($truthy(self['$absolute?']()) ? ("/") : ("."));
      }      return $$('Pathname').$new(new_path);
    });
    
    $def(self, '$sub', function $$sub($a) {
      var $post_args, args, self = this;

      
      $post_args = $slice(arguments);
      args = $post_args;
      return $$('Pathname').$new($send(self.path, 'sub', $to_a(args)));
    }, -1);
    
    $def(self, '$cleanpath', function $$cleanpath() {
      var self = this;

      return Opal.normalize(self.path)
    });
    
    $def(self, '$to_path', $return_ivar("path"));
    
    $def(self, '$hash', $return_ivar("path"));
    
    $def(self, '$expand_path', function $$expand_path() {
      var self = this;

      return $$('Pathname').$new($$('File').$expand_path(self.path))
    });
    
    $def(self, '$+', function $Pathname_$plus$5(other) {
      var self = this;

      
      if (!$eqeqeq($$('Pathname'), other)) {
        other = $$('Pathname').$new(other);
      }      return $$('Pathname').$new(self.$plus(self.path, other.$to_s()));
    });
    
    $def(self, '$plus', function $$plus(path1, path2) {
      var $a, $b, self = this, prefix2 = nil, index_list2 = nil, basename_list2 = nil, r2 = nil, basename2 = nil, prefix1 = nil, $ret_or_1 = nil, r1 = nil, basename1 = nil, suffix2 = nil;

      
      prefix2 = path2;
      index_list2 = [];
      basename_list2 = [];
      while ($truthy((r2 = self.$chop_basename(prefix2)))) {
      
        $b = r2, $a = $to_ary($b), (prefix2 = ($a[0] == null ? nil : $a[0])), (basename2 = ($a[1] == null ? nil : $a[1]));
        index_list2.$unshift(prefix2.$length());
        basename_list2.$unshift(basename2);
      }      if ($neqeq(prefix2, "")) {
        return path2
      }      prefix1 = path1;
      while ($truthy(true)) {
      
        while ($truthy(($truthy(($ret_or_1 = basename_list2['$empty?']()['$!']())) ? (basename_list2.$first()['$=='](".")) : ($ret_or_1)))) {
        
          index_list2.$shift();
          basename_list2.$shift();
        }        if (!$truthy((r1 = self.$chop_basename(prefix1)))) {
          break
        }        $b = r1, $a = $to_ary($b), (prefix1 = ($a[0] == null ? nil : $a[0])), (basename1 = ($a[1] == null ? nil : $a[1]));
        if ($eqeq(basename1, ".")) {
          continue
        }        if ((($eqeq(basename1, "..") || ($truthy(basename_list2['$empty?']()))) || ($neqeq(basename_list2.$first(), "..")))) {
          
          prefix1 = $rb_plus(prefix1, basename1);
          break;
        }        index_list2.$shift();
        basename_list2.$shift();
      }      r1 = self.$chop_basename(prefix1);
      if (($not(r1) && ($truthy($regexp([$$('SEPARATOR_PAT')])['$=~']($$('File').$basename(prefix1)))))) {
        while ($truthy(($truthy(($ret_or_1 = basename_list2['$empty?']()['$!']())) ? (basename_list2.$first()['$==']("..")) : ($ret_or_1)))) {
        
          index_list2.$shift();
          basename_list2.$shift();
        }
      }      if ($not(basename_list2['$empty?']())) {
        
        suffix2 = path2['$[]'](Opal.Range.$new(index_list2.$first(), -1, false));
        if ($truthy(r1)) {
          return $$('File').$join(prefix1, suffix2)
        } else {
          return $rb_plus(prefix1, suffix2)
        }      } else if ($truthy(r1)) {
        return prefix1
      } else {
        return $$('File').$dirname(prefix1)
      }    });
    
    $def(self, '$join', function $$join($a) {try { var $t_return = $thrower('return'); 
      var $post_args, args, self = this, result = nil;

      
      $post_args = $slice(arguments);
      args = $post_args;
      if ($truthy(args['$empty?']())) {
        return self
      };
      result = args.$pop();
      if (!$eqeqeq($$('Pathname'), result)) {
        result = $$('Pathname').$new(result);
      };
      if ($truthy(result['$absolute?']())) {
        return result
      };
      $send(args, 'reverse_each', [], function $$6(arg){
        
        if (arg == null) arg = nil;
        if (!$eqeqeq($$('Pathname'), arg)) {
          arg = $$('Pathname').$new(arg);
        };
        result = $rb_plus(arg, result);
        if ($truthy(result['$absolute?']())) {
          $t_return.$throw(result);
        } else {
          return nil
        };}, {$$ret: $t_return});
      return $rb_plus(self, result);} catch($e) {
        if ($e === $t_return) return $e.$v;
        throw $e;
      }
    }, -1);
    
    $def(self, '$split', function $$split() {
      var self = this;

      return [self.$dirname(), self.$basename()]
    });
    
    $def(self, '$dirname', function $$dirname() {
      var self = this;

      return $$('Pathname').$new($$('File').$dirname(self.path))
    });
    
    $def(self, '$basename', function $$basename() {
      var self = this;

      return $$('Pathname').$new($$('File').$basename(self.path))
    });
    
    $def(self, '$directory?', function $Pathname_directory$ques$7() {
      var self = this;

      return $$('File')['$directory?'](self.path)
    });
    
    $def(self, '$extname', function $$extname() {
      var self = this;

      return $$('File').$extname(self.path)
    });
    
    $def(self, '$<=>', function $Pathname_$lt_eq_gt$8(other) {
      var self = this;

      return self.$path()['$<=>'](other.$path())
    });
    $const_set($nesting[0], 'SAME_PATHS', ($truthy($$$($$('File'), 'FNM_SYSCASE')['$nonzero?']()) ? ($send(self, 'proc', [], function $Pathname$9(a, b){
      
      if (a == null) a = nil;
      if (b == null) b = nil;
      return a.$casecmp(b)['$=='](0);})) : ($send(self, 'proc', [], function $Pathname$10(a, b){
      
      if (a == null) a = nil;
      if (b == null) b = nil;
      return a['$=='](b);}))));
    
    $def(self, '$relative_path_from', function $$relative_path_from(base_directory) {
      var $a, $b, self = this, dest_directory = nil, dest_prefix = nil, dest_names = nil, r = nil, basename = nil, base_prefix = nil, base_names = nil, $ret_or_1 = nil, $ret_or_2 = nil, relpath_names = nil;

      
      dest_directory = self.$cleanpath().$to_s();
      base_directory = base_directory.$cleanpath().$to_s();
      dest_prefix = dest_directory;
      dest_names = [];
      while ($truthy((r = self.$chop_basename(dest_prefix)))) {
      
        $b = r, $a = $to_ary($b), (dest_prefix = ($a[0] == null ? nil : $a[0])), (basename = ($a[1] == null ? nil : $a[1]));
        if ($neqeq(basename, ".")) {
          dest_names.$unshift(basename);
        }      }      base_prefix = base_directory;
      base_names = [];
      while ($truthy((r = self.$chop_basename(base_prefix)))) {
      
        $b = r, $a = $to_ary($b), (base_prefix = ($a[0] == null ? nil : $a[0])), (basename = ($a[1] == null ? nil : $a[1]));
        if ($neqeq(basename, ".")) {
          base_names.$unshift(basename);
        }      }      if (!$truthy($$('SAME_PATHS')['$[]'](dest_prefix, base_prefix))) {
        self.$raise($$('ArgumentError'), "different prefix: " + (dest_prefix.$inspect()) + " and " + (base_directory.$inspect()));
      }      while ($truthy(($truthy(($ret_or_1 = ($truthy(($ret_or_2 = dest_names['$empty?']()['$!']())) ? (base_names['$empty?']()['$!']()) : ($ret_or_2)))) ? ($$('SAME_PATHS')['$[]'](dest_names.$first(), base_names.$first())) : ($ret_or_1)))) {
      
        dest_names.$shift();
        base_names.$shift();
      }      if ($truthy(base_names['$include?'](".."))) {
        self.$raise($$('ArgumentError'), "base_directory has ..: " + (base_directory.$inspect()));
      }      base_names.$fill("..");
      relpath_names = $rb_plus(base_names, dest_names);
      if ($truthy(relpath_names['$empty?']())) {
        return $$('Pathname').$new(".")
      } else {
        return $$('Pathname').$new($send($$('File'), 'join', $to_a(relpath_names)))
      }    });
    
    $def(self, '$entries', function $$entries() {
      var self = this;

      return $send($$('Dir').$entries(self.path), 'map', [], function $$11(f){var self = $$11.$$s == null ? this : $$11.$$s;

        
        if (f == null) f = nil;
        return self.$class().$new(f);}, {$$s: self})
    });
    $alias(self, "===", "==");
    $alias(self, "eql?", "==");
    $alias(self, "to_s", "to_path");
    return $alias(self, "to_str", "to_path");
  })($nesting[0], null, $nesting);
  return (function($base, $parent_nesting) {
    var self = $module($base, 'Kernel');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting);

    return $def(self, '$Pathname', function $$Pathname(path) {
      
      return $$('Pathname').$new(path)
    })
  })($nesting[0], $nesting);
};

Opal.modules["stringio"] = function(Opal) {/* Generated by Opal 1.7.3 */
  var $klass = Opal.klass, $defs = Opal.defs, $send2 = Opal.send2, $find_super = Opal.find_super, $def = Opal.def, $eqeqeq = Opal.eqeqeq, $truthy = Opal.truthy, $rb_ge = Opal.rb_ge, $rb_gt = Opal.rb_gt, $rb_plus = Opal.rb_plus, $rb_minus = Opal.rb_minus, $return_ivar = Opal.return_ivar, $eqeq = Opal.eqeq, $alias = Opal.alias, $nesting = [], $$ = Opal.$r($nesting), nil = Opal.nil, $$$ = Opal.$$$;

  Opal.add_stubs('new,call,close,attr_accessor,check_readable,==,length,===,>=,raise,>,+,-,seek,check_writable,String,[],eof?,write,read,tell');
  return (function($base, $super, $parent_nesting) {
    var self = $klass($base, $super, 'StringIO');

    var $nesting = [self].concat($parent_nesting), $$ = Opal.$r($nesting), $proto = self.$$prototype;

    $proto.position = $proto.string = nil;
    
    $defs(self, '$open', function $$open(string, mode) {
      var block = $$open.$$p || nil, self = this, io = nil, res = nil;

      $$open.$$p = null;
      if (string == null) string = "";
      if (mode == null) mode = nil;
      io = self.$new(string, mode);
      res = block.$call(io);
      io.$close();
      return res;
    }, -1);
    self.$attr_accessor("string");
    
    $def(self, '$initialize', function $$initialize(string, mode) {
      var self = this;

      $$initialize.$$p = null;
      
      if (string == null) string = "";
      if (mode == null) mode = "rw";
      self.string = string;
      self.position = 0;
      return $send2(self, $find_super(self, 'initialize', $$initialize, false, true), 'initialize', [nil, mode], null);
    }, -1);
    
    $def(self, '$eof?', function $StringIO_eof$ques$1() {
      var self = this;

      
      self.$check_readable();
      return self.position['$=='](self.string.$length());
    });
    
    $def(self, '$seek', function $$seek(pos, whence) {
      var self = this, $ret_or_1 = nil;

      
      if (whence == null) whence = $$$($$('IO'), 'SEEK_SET');
      self.read_buffer = "";
      if ($eqeqeq($$$($$('IO'), 'SEEK_SET'), ($ret_or_1 = whence))) {
        
        if (!$truthy($rb_ge(pos, 0))) {
          self.$raise($$$($$('Errno'), 'EINVAL'));
        }        self.position = pos;
      } else if ($eqeqeq($$$($$('IO'), 'SEEK_CUR'), $ret_or_1)) {
        if ($truthy($rb_gt($rb_plus(self.position, pos), self.string.$length()))) {
          self.position = self.string.$length();
        } else {
          self.position = $rb_plus(self.position, pos);
        }
      } else if ($eqeqeq($$$($$('IO'), 'SEEK_END'), $ret_or_1)) {
        if ($truthy($rb_gt(pos, self.string.$length()))) {
          self.position = 0;
        } else {
          self.position = $rb_minus(self.position, pos);
        }
      } else ;      return 0;
    }, -2);
    
    $def(self, '$tell', $return_ivar("position"));
    
    $def(self, '$rewind', function $$rewind() {
      var self = this;

      return self.$seek(0)
    });
    
    $def(self, '$write', function $$write(string) {
      var self = this, before = nil, after = nil;

      
      self.$check_writable();
      self.read_buffer = "";
      string = self.$String(string);
      if ($eqeq(self.string.$length(), self.position)) {
        
        self.string = $rb_plus(self.string, string);
        return (self.position = $rb_plus(self.position, string.$length()));
      } else {
        
        before = self.string['$[]'](Opal.Range.$new(0, $rb_minus(self.position, 1), false));
        after = self.string['$[]'](Opal.Range.$new($rb_plus(self.position, string.$length()), -1, false));
        self.string = $rb_plus($rb_plus(before, string), after);
        return (self.position = $rb_plus(self.position, string.$length()));
      }    });
    
    $def(self, '$read', function $$read(length, outbuf) {
      var self = this, string = nil, str = nil;

      
      if (length == null) length = nil;
      if (outbuf == null) outbuf = nil;
      self.$check_readable();
      if ($truthy(self['$eof?']())) {
        return nil
      }      string = ($truthy(length) ? (((str = self.string['$[]'](self.position, length)), (self.position = $rb_plus(self.position, length)), ($truthy($rb_gt(self.position, self.string.$length())) ? ((self.position = self.string.$length())) : nil), str)) : (((str = self.string['$[]'](Opal.Range.$new(self.position, -1, false))), (self.position = self.string.$length()), str)));
      if ($truthy(outbuf)) {
        return outbuf.$write(string)
      } else {
        return string
      }    }, -1);
    
    $def(self, '$sysread', function $$sysread(length) {
      var self = this;

      
      self.$check_readable();
      return self.$read(length);
    });
    $alias(self, "eof", "eof?");
    $alias(self, "pos", "tell");
    $alias(self, "pos=", "seek");
    return $alias(self, "readpartial", "read");
  })($nesting[0], $$('IO'), $nesting)
};

export { Opal$1 as default };
